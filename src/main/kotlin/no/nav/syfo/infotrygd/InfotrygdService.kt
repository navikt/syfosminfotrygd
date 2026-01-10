package no.nav.syfo.infotrygd

import jakarta.jms.Session
import java.math.BigInteger
import java.time.LocalDate
import java.util.UUID
import no.nav.helse.eiFellesformat.XMLEIFellesformat
import no.nav.helse.infotrygd.foresp.InfotrygdForesp
import no.nav.helse.sm2013.KontrollSystemBlokk
import no.nav.helse.sm2013.KontrollsystemBlokkType
import no.nav.syfo.ServiceUser
import no.nav.syfo.log
import no.nav.syfo.mq.MqConfig
import no.nav.syfo.mq.connectionFactory
import no.nav.syfo.mq.producerForQueue
import no.nav.syfo.objectMapper
import no.nav.syfo.rules.validation.sortedSMInfos
import no.nav.syfo.services.FinnNAVKontorService
import no.nav.syfo.services.InfotrygdForespValues
import no.nav.syfo.services.createInfotrygdForesp
import no.nav.syfo.services.sendInfotrygdForesporsel
import no.nav.syfo.services.sikkerlogg
import no.nav.syfo.services.updateinfotrygd.findoperasjonstypeAndFom
import no.nav.syfo.services.updateinfotrygd.formatName
import no.nav.syfo.util.LoggingMeta
import no.nav.syfo.util.xmlObjectWriter

data class InfotrygdResponse(
    val identDato: String?,
    val tkNummer: String?,
)

data class UpdateResponse(
    val oppdatert: Boolean,
    val canUpdate: Boolean,
    val message: String,
)

class InfotrygdService(
    private val finnNAVKontorService: FinnNAVKontorService,
    private val serviceUser: ServiceUser,
    private val mqConfig: MqConfig,
    private val infotrygdSporringQueue: String,
    private val infotrygdOppdateringQueue: String
) {

    val connection =
        connectionFactory(mqConfig)
            .createConnection(serviceUser.serviceuserUsername, serviceUser.serviceuserPassword)
    val session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)
    val producer = session.producerForQueue(infotrygdOppdateringQueue)

    init {
        connection.start()
    }

    fun sendInfotrygdForesporsel(infotrygdQuery: InfotrygdQuery): InfotrygdResponse {
        val infotrygdResult = getInfotrygdForesp(infotrygdQuery)

        return InfotrygdResponse(
            identDato =
                infotrygdResult.sMhistorikk
                    ?.sykmelding
                    ?.sortedSMInfos()
                    ?.lastOrNull()
                    ?.periode
                    ?.arbufoerFOM
                    ?.toString(),
            tkNummer = infotrygdResult.tkNummer
        )
    }

    fun sendInfotrygdBlock(fellesformat: XMLEIFellesformat) {
        producer.send(
            session.createTextMessage().apply {
                text = xmlObjectWriter.writeValueAsString(fellesformat)
            },
        )
    }

    fun getInfotrygdForesp(infotrygdQuery: InfotrygdQuery): InfotrygdForesp {

        val infotrygdSporringProducer =
            session.producerForQueue(
                "queue:///${infotrygdSporringQueue}?targetClient=1",
            )
        val infotrygdForespValues = InfotrygdForespValues.from(infotrygdQuery)

        val infotrygdForesp =
            createInfotrygdForesp(
                infotrygdQuery.ident,
                infotrygdForespValues,
                doctorFnr = infotrygdQuery.fodselsnrBehandler,
                infotrygdQuery.tknumber,
            )
        val infotrygdResult =
            sendInfotrygdForesporsel(
                session,
                infotrygdSporringProducer,
                infotrygdForesp,
                UUID.randomUUID().toString(),
            )
        return infotrygdResult
    }

    suspend fun tryUpdateInfotrygd(updateRequest: InfotrygdUpdateRequest): UpdateResponse {

        val loggingMeta =
            LoggingMeta(
                mottakId = updateRequest.mottakId,
                orgNr = null,
                msgId = updateRequest.msgId,
                sykmeldingId = updateRequest.sykmeldingId
            )
        val navKontor =
            updateRequest.navKontorNr
                ?: finnNAVKontorService.finnLokaltNavkontor(
                    updateRequest.fnr,
                    loggingMeta = loggingMeta
                )

        val infotrygdResponse = getInfotrygdForesp(InfotrygdQuery(ident = updateRequest.fnr))

        sikkerlogg.info(
            "UpdateInfotrygdRequest ${objectMapper.writeValueAsString(updateRequest)}, infotrygdResponse  ${objectMapper.writeValueAsString(infotrygdResponse)}"
        )

        val oprasjonstype =
            findoperasjonstypeAndFom(
                updateRequest.fom,
                updateRequest.tom,
                infotrygdResponse.sMhistorikk?.sykmelding?.sortedSMInfos() ?: emptyList()
            )

        val sykmeldingToUpdate =
            infotrygdResponse.sMhistorikk?.sykmelding?.find {
                val equalFomAndTom =
                    oprasjonstype.second.equals(it.periode.arbufoerFOM) &&
                        updateRequest.tom.equals(it.periode.arbufoerTOM)
                equalFomAndTom
            }

        if (sykmeldingToUpdate == null) {
            return UpdateResponse(false, false, "Could not find sykmelding to update")
        }

        sikkerlogg.info(
            "Found sykmelding to update for ${objectMapper.writeValueAsString(updateRequest)}, infotrygdResponse  ${objectMapper.writeValueAsString(sykmeldingToUpdate)}"
        )

        if (updateRequest.dryRun) {
            log.info("Dry run, not updating")
            return UpdateResponse(false, canUpdate = true, "Dry run, not updating")
        }
        val fellesformat =
            getInfotrygdBlock(
                ident = updateRequest.fnr,
                tkNummers = navKontor,
                oprasjonstype = 2,
                identDato = updateRequest.fom,
                tom = updateRequest.toTom,
                uforegrad = sykmeldingToUpdate.periode.ufoeregrad.toInt(),
                tssIdent = sykmeldingToUpdate.periode.legeInstNr ?: "0".toBigInteger(),
                legeNavn = sykmeldingToUpdate.periode.legeNavn?.formatName() ?: ""
            )

        sendInfotrygdBlock(fellesformat)

        log.info(
            "Updating sykmelding with fom: ${sykmeldingToUpdate.periode.arbufoerFOM}, and tom: ${sykmeldingToUpdate.periode.arbufoerTOM} from infotrygd"
        )
        return UpdateResponse(
            true,
            true,
            "Updated sykmelding with fom: ${sykmeldingToUpdate.periode.arbufoerFOM}, and tom: ${sykmeldingToUpdate.periode.arbufoerTOM} from infotrygd"
        )
    }

    fun sendInfotrygdForesporselWithDetails(query: InfotrygdQuery): InfotrygdForesp {
        val infotrygdResult = getInfotrygdForesp(query)
        return infotrygdResult
    }
}

private fun getInfotrygdBlock(
    ident: String,
    tkNummers: String?,
    oprasjonstype: Int,
    identDato: LocalDate,
    tom: LocalDate,
    uforegrad: Int,
    tssIdent: BigInteger,
    legeNavn: String
): XMLEIFellesformat {
    return XMLEIFellesformat().apply {
        any.add(
            KontrollSystemBlokk().apply {
                infotrygdBlokk.add(
                    KontrollsystemBlokkType.InfotrygdBlokk().apply {
                        fodselsnummer = ident
                        tkNummer = tkNummers
                        operasjonstype = oprasjonstype.toBigInteger()
                        forsteFravaersDag = identDato
                        arbeidsufoerTOM = tom
                        ufoeregrad = uforegrad.toBigInteger()
                        legeEllerInstitusjonsNummer = tssIdent
                        legeEllerInstitusjon = legeNavn
                        mottakerKode = "LE"
                    }
                )
            }
        )
    }
}
