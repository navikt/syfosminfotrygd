package no.nav.syfo.infotrygd

import jakarta.jms.Connection
import java.util.UUID
import no.nav.syfo.model.Diagnosekode
import no.nav.syfo.rules.validation.sortedSMInfos
import no.nav.syfo.services.InfotrygdForespValues
import no.nav.syfo.services.createInfotrygdForesp
import no.nav.syfo.services.sendInfotrygdForesporsel

data class InfotrygdResponse(
    val identDato: String?,
    val tkNummer: String?,
)

class InfotrygdService(private val connection: Connection, private val infotrygdQueueName: String) {

    fun sendInfotrygdForesporsel(infotrygdQuery: InfotrygdQuery): InfotrygdResponse {
        val session = connection.createSession()
        val producer = session.createProducer(session.createQueue(infotrygdQueueName))
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
                producer,
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
    }
}
