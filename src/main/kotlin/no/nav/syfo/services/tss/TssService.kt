package no.nav.syfo.services.tss

import net.logstash.logback.argument.StructuredArguments
import no.nav.helse.infotrygd.foresp.InfotrygdForesp
import no.nav.helse.infotrygd.foresp.TypeSMinfo
import no.nav.syfo.erUtenlandskSykmelding
import no.nav.syfo.log
import no.nav.syfo.model.Behandler
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.rules.validation.sortedSMInfos
import no.nav.syfo.util.LoggingMeta
import javax.jms.MessageProducer
import javax.jms.Session

suspend fun getTssId(
    infotrygdForespResponse: InfotrygdForesp,
    receivedSykmelding: ReceivedSykmelding,
    tssProducer: MessageProducer,
    session: Session,
    loggingMeta: LoggingMeta,
): String? {
    val tssIdInfotrygd = finnTssIdFraInfotrygdRespons(
        infotrygdForespResponse.sMhistorikk?.sykmelding?.sortedSMInfos()?.lastOrNull()?.periode,
        receivedSykmelding.sykmelding.behandler,
    )
    if (!tssIdInfotrygd.isNullOrBlank() && !receivedSykmelding.erUtenlandskSykmelding()) {
        log.info(
            "Sykmelding mangler tssid, har hentet tssid $tssIdInfotrygd fra infotrygd, {}",
            StructuredArguments.fields(loggingMeta),
        )
        return tssIdInfotrygd
    } else if (receivedSykmelding.erUtenlandskSykmelding()) {
        log.info(
            "Bruker standardverdi for tssid for utenlandsk sykmelding, {}",
            StructuredArguments.fields(loggingMeta),
        )
        return "0"
    } else {
        try {
            val tssIdFraTSS = fetchTssSamhandlerInfo(receivedSykmelding, tssProducer, session)
            if (!tssIdFraTSS.isNullOrBlank()) {
                log.info(
                    "Sykmelding mangler tssid, har hentet tssid $tssIdFraTSS fra tss, {}",
                    StructuredArguments.fields(loggingMeta),
                )
                return tssIdFraTSS
            }
            log.info("Fant ingen tssider fra TSS!!!")
        } catch (e: Exception) {
            log.error("Kall mot TSS gikk dårligt", e)
        }
        return null
    }
}

fun finnTssIdFraInfotrygdRespons(sisteSmPeriode: TypeSMinfo.Periode?, behandler: Behandler): String? {
    if (sisteSmPeriode != null &&
        behandler.etternavn.equals(sisteSmPeriode.legeNavn?.etternavn, true) &&
        behandler.fornavn.equals(sisteSmPeriode.legeNavn?.fornavn, true)
    ) {
        return sisteSmPeriode.legeInstNr?.toString()
    }
    return null
}
