package no.nav.syfo.services

import no.nav.helse.infotrygd.foresp.InfotrygdForesp
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.smregister.MerknadType
import no.nav.syfo.smregister.PeriodetypeDTO
import no.nav.syfo.smregister.RegelStatusDTO
import no.nav.syfo.smregister.SmregisterClient
import no.nav.syfo.smregister.SykmeldingDTO
import java.time.LocalDate

class SykmeldingService(private val smregisterClient: SmregisterClient) {

    fun hasOverlappingPeriodsFromInfotrygd(receivedSykmelding: ReceivedSykmelding, infotrygdForesp: InfotrygdForesp): Boolean {
        val fom = receivedSykmelding.sykmelding.perioder.sortedFOMDate().first()
        val tom = receivedSykmelding.sykmelding.perioder.sortedTOMDate().last()
        val sykmeldingDays = getWorkdays(fom, tom)

        val infotrygdDays = infotrygdForesp.sMhistorikk?.sykmelding?.filter {
            it.periode.arbufoerTOM != null &&
                fom <= it.periode.arbufoerTOM
        }?.filter {
            it.periode.arbufoerFOM != null &&
                tom >= it.periode.arbufoerFOM
        }?.flatMap { getWorkdays(it.periode.arbufoerFOM, it.periode.arbufoerTOM) }
            ?.distinct() ?: emptyList()

        return infotrygdDays.containsAll(sykmeldingDays)
    }

    suspend fun hasOverlappingPeriodsFromRegister(receivedSykmelding: ReceivedSykmelding): Boolean {
        val fom = receivedSykmelding.sykmelding.perioder.sortedFOMDate().first()
        val tom = receivedSykmelding.sykmelding.perioder.sortedTOMDate().last()
        val sykmeldingDays = getWorkdays(fom, tom)
        val previousDays =
            smregisterClient.getSykmeldinger(receivedSykmelding.personNrPasient, fom, tom)
                .filterNot { harTilbakedatertMerknad(it) }
                .filterNot { it.behandlingsutfall.status == RegelStatusDTO.INVALID }
                .flatMap { sykmelding ->
                    sykmelding.sykmeldingsperioder.filter { periode ->
                        periode.type == PeriodetypeDTO.AKTIVITET_IKKE_MULIG ||
                            periode.type == PeriodetypeDTO.GRADERT
                    }
                }
                .flatMap { getWorkdays(it.fom, it.tom) }
                .distinct()

        return previousDays.containsAll(sykmeldingDays)
    }

    private fun getWorkdays(
        fom: LocalDate,
        tom: LocalDate,
    ): MutableList<LocalDate> =
        fom.datesUntil(tom.plusDays(1)).filter { date ->
            date.dayOfWeek.value < 6
        }.toList()

    private fun harTilbakedatertMerknad(sykmelding: SykmeldingDTO): Boolean {
        return sykmelding.merknader?.any {
            MerknadType.contains(it.type)
        } ?: false
    }
}
