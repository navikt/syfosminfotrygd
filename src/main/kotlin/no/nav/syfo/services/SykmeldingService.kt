package no.nav.syfo.services

import java.time.LocalDate
import no.nav.helse.infotrygd.foresp.TypeSMinfo
import no.nav.syfo.smregister.MerknadType
import no.nav.syfo.smregister.SykmeldingDTO

fun hasOverlappingPeriodsFromInfotrygd(
    fom: LocalDate,
    tom: LocalDate,
    infotrygdPerioder: List<TypeSMinfo>
): Boolean {
    val sykmeldingDays = getWorkdays(fom, tom)

    val infotrygdDays =
        infotrygdPerioder
            .filter { it.periode.arbufoerTOM != null && fom <= it.periode.arbufoerTOM }
            .filter { it.periode.arbufoerFOM != null && tom >= it.periode.arbufoerFOM }
            .flatMap { getWorkdays(it.periode.arbufoerFOM, it.periode.arbufoerTOM) }
            .distinct()

    return infotrygdDays.containsAll(sykmeldingDays)
}

private fun getWorkdays(
    fom: LocalDate,
    tom: LocalDate,
): MutableList<LocalDate> =
    fom.datesUntil(tom.plusDays(1)).filter { date -> date.dayOfWeek.value < 6 }.toList()

private fun harTilbakedatertMerknad(sykmelding: SykmeldingDTO): Boolean {
    return sykmelding.merknader?.any { MerknadType.contains(it.type) } ?: false
}
