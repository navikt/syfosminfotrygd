package no.nav.syfo.infotrygd

import jakarta.jms.Session
import java.util.UUID
import no.nav.syfo.ServiceUser
import no.nav.syfo.model.Diagnosekode
import no.nav.syfo.mq.MqConfig
import no.nav.syfo.mq.connectionFactory
import no.nav.syfo.mq.producerForQueue
import no.nav.syfo.rules.validation.sortedSMInfos
import no.nav.syfo.services.InfotrygdForespValues
import no.nav.syfo.services.createInfotrygdForesp
import no.nav.syfo.services.sendInfotrygdForesporsel
import org.slf4j.LoggerFactory

data class InfotrygdResponse(
    val identDato: String?,
    val tkNummer: String?,
)

class InfotrygdService(
    private val serviceUser: ServiceUser,
    private val mqConfig: MqConfig,
    private val infotrygdSporringQueue: String
) {
    companion object {
        private val log = LoggerFactory.getLogger(InfotrygdService::class.java)
    }
    fun sendInfotrygdForesporsel(infotrygdQuery: InfotrygdQuery): InfotrygdResponse {
        val connection =
            connectionFactory(mqConfig)
                .createConnection(serviceUser.serviceuserUsername, serviceUser.serviceuserPassword)

        connection.start()
        val session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)

        val infotrygdSporringProducer =
            session.producerForQueue(
                "queue:///${infotrygdSporringQueue}?targetClient=1",
            )
        try {
            val infotrygdForespValues =
                InfotrygdForespValues(
                    hovedDiagnosekode = infotrygdQuery.hoveddiagnose?.kode,
                    hovedDiagnosekodeverk =
                        infotrygdQuery.hoveddiagnose?.system?.let { system ->
                            Diagnosekode.entries.first { it.kithCode == system }.infotrygdCode
                        },
                    biDiagnoseKode = infotrygdQuery.bidiagnose?.kode,
                    biDiagnosekodeverk =
                        infotrygdQuery.bidiagnose?.system?.let { system ->
                            Diagnosekode.entries.first { it.kithCode == system }.infotrygdCode
                        },
                )
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
                    UUID.randomUUID().toString()
                )

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
        } catch (ex: Exception) {
            log.warn("Exception caught while attempting to send infotrygd sporring", ex)
            throw ex
        } finally {
            infotrygdSporringProducer.close()
            session.close()
            connection.close()
        }
    }
}
