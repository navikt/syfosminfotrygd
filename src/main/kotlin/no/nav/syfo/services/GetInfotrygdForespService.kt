package no.nav.syfo.services

import com.ctc.wstx.exc.WstxException
import no.nav.helse.infotrygd.foresp.InfotrygdForesp
import no.nav.helse.sm2013.HelseOpplysningerArbeidsuforhet
import no.nav.syfo.helpers.retry
import no.nav.syfo.log
import no.nav.syfo.model.Diagnosekode
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.toString
import no.nav.syfo.util.infotrygdSporringMarshaller
import no.nav.syfo.util.infotrygdSporringUnmarshaller
import java.io.IOException
import java.io.StringReader
import java.lang.IllegalStateException
import java.time.LocalDate
import java.time.LocalDateTime
import javax.jms.MessageProducer
import javax.jms.Session
import javax.jms.TemporaryQueue
import javax.jms.TextMessage

suspend fun fetchInfotrygdForesp(
    receivedSykmelding: ReceivedSykmelding,
    infotrygdSporringProducer: MessageProducer,
    session: Session,
    healthInformation: HelseOpplysningerArbeidsuforhet
): InfotrygdForesp =
    retry(
        callName = "it_hent_infotrygdForesp",
        retryIntervals = arrayOf(500L, 1000L, 3000L, 5000L, 10000L),
        legalExceptions = arrayOf(IOException::class, WstxException::class, IllegalStateException::class)
    ) {
        val infotrygdForespRequest = createInfotrygdForesp(receivedSykmelding.personNrPasient, healthInformation, finnLegeFnr(receivedSykmelding))
        val temporaryQueue = session.createTemporaryQueue()
        try {
            sendInfotrygdSporring(infotrygdSporringProducer, session, infotrygdForespRequest, temporaryQueue)
            session.createConsumer(temporaryQueue).use { tmpConsumer ->
                val consumedMessage = tmpConsumer.receive(20000)
                val inputMessageText = when (consumedMessage) {
                    is TextMessage -> consumedMessage.text
                    else -> throw RuntimeException("Incoming message needs to be a byte message or text message, JMS type:" + consumedMessage.jmsType)
                }

                infotrygdSporringUnmarshaller.unmarshal(StringReader(inputMessageText)) as InfotrygdForesp
            }
        } finally {
            temporaryQueue.delete()
        }
    }

fun createInfotrygdForesp(personNrPasient: String, healthInformation: HelseOpplysningerArbeidsuforhet, doctorFnr: String) = InfotrygdForesp().apply {
    val dateMinus1Year = LocalDate.now().minusYears(1)
    val dateMinus4Years = LocalDate.now().minusYears(4)

    fodselsnummer = personNrPasient
    tkNrFraDato = dateMinus1Year
    forespNr = 1.toBigInteger()
    forespTidsStempel = LocalDateTime.now()
    fraDato = dateMinus1Year
    eldsteFraDato = dateMinus4Years
    hovedDiagnosekode = healthInformation.medisinskVurdering.hovedDiagnose.diagnosekode.v
    hovedDiagnosekodeverk = Diagnosekode.values().first {
        it.kithCode == healthInformation.medisinskVurdering.hovedDiagnose.diagnosekode.s
    }.infotrygdCode
    fodselsnrBehandler = doctorFnr
    if (healthInformation.medisinskVurdering.biDiagnoser?.diagnosekode?.firstOrNull()?.v != null &&
        healthInformation.medisinskVurdering.biDiagnoser?.diagnosekode?.firstOrNull()?.s != null
    ) {
        biDiagnoseKode = healthInformation.medisinskVurdering.biDiagnoser.diagnosekode.first().v
        biDiagnosekodeverk = Diagnosekode.values().first {
            it.kithCode == healthInformation.medisinskVurdering.biDiagnoser.diagnosekode.first().s
        }.infotrygdCode
    }
    tkNrFraDato = dateMinus1Year
}

fun finnLegeFnr(receivedSykmelding: ReceivedSykmelding): String {
    return if (receivedSykmelding.sykmelding.avsenderSystem.navn == "Egenmeldt") {
        // Testfamilien Aremark stepper inn som lege for egenmeldte sykmeldinger
        log.info("Setter Aremark som lege for egenmeldt sykmelding med id {}", receivedSykmelding.sykmelding.id)
        "10108000398"
    } else if (receivedSykmelding.utenlandskSykmelding != null) {
        log.info("Setter standardverdi for behandler for utenlandsk sykmelding med id ${receivedSykmelding.sykmelding.id}")
        "LU ${receivedSykmelding.utenlandskSykmelding!!.land}"
    } else {
        receivedSykmelding.personNrLege
    }
}

fun sendInfotrygdSporring(
    producer: MessageProducer,
    session: Session,
    infotrygdForesp: InfotrygdForesp,
    temporaryQueue: TemporaryQueue
) = producer.send(
    session.createTextMessage().apply {
        text = infotrygdSporringMarshaller.toString(infotrygdForesp)
        jmsReplyTo = temporaryQueue
    }
)
