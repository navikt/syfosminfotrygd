package no.nav.syfo.services

import com.ctc.wstx.exc.WstxException
import io.opentelemetry.instrumentation.annotations.WithSpan
import jakarta.jms.MessageProducer
import jakarta.jms.Session
import jakarta.jms.TemporaryQueue
import jakarta.jms.TextMessage
import java.io.IOException
import java.io.StringReader
import java.time.LocalDate
import java.time.LocalDateTime
import javax.xml.parsers.SAXParserFactory
import javax.xml.transform.Source
import javax.xml.transform.sax.SAXSource
import no.nav.helse.infotrygd.foresp.InfotrygdForesp
import no.nav.helse.sm2013.HelseOpplysningerArbeidsuforhet
import no.nav.syfo.UTENLANDSK_SYKEHUS
import no.nav.syfo.erUtenlandskSykmelding
import no.nav.syfo.helpers.retry
import no.nav.syfo.log
import no.nav.syfo.model.Diagnosekode
import no.nav.syfo.model.sykmelding.ReceivedSykmelding
import no.nav.syfo.toString
import no.nav.syfo.util.infotrygdSporringMarshaller
import no.nav.syfo.util.infotrygdSporringUnmarshaller
import org.xml.sax.InputSource

@WithSpan
suspend fun fetchInfotrygdForesp(
    receivedSykmelding: ReceivedSykmelding,
    infotrygdSporringProducer: MessageProducer,
    session: Session,
    healthInformation: HelseOpplysningerArbeidsuforhet,
): InfotrygdForesp =
    retry(
        callName = "it_hent_infotrygdForesp",
        retryIntervals = arrayOf(500L, 1000L, 3000L, 5000L, 10000L),
        legalExceptions =
            arrayOf(IOException::class, WstxException::class, IllegalStateException::class),
    ) {
        val infotrygdForespRequest =
            createInfotrygdForesp(
                receivedSykmelding.personNrPasient,
                healthInformation,
                finnLegeFnrFra(receivedSykmelding),
            )
        val temporaryQueue = session.createTemporaryQueue()
        try {
            sendInfotrygdSporring(
                infotrygdSporringProducer,
                session,
                infotrygdForespRequest,
                temporaryQueue,
            )
            session.createConsumer(temporaryQueue).use { tmpConsumer ->
                val consumedMessage = tmpConsumer.receive(20000)
                val inputMessageText =
                    when (consumedMessage) {
                        null ->
                            throw RuntimeException("Incoming message is null")
                        is TextMessage -> consumedMessage.text
                        else -> throw RuntimeException(
                            "Incoming message needs to be a byte message or text message, JMS type: " +
                                consumedMessage.jmsType,
                        )
                    }
                safeUnmarshal(inputMessageText, receivedSykmelding.sykmelding.id)
            }
        } finally {
            temporaryQueue.delete()
        }
    }

fun createInfotrygdForesp(
    personNrPasient: String,
    healthInformation: HelseOpplysningerArbeidsuforhet,
    doctorFnr: String
) =
    InfotrygdForesp().apply {
        val dateMinus1Year = LocalDate.now().minusYears(1)
        val dateMinus4Years = LocalDate.now().minusYears(4)

        fodselsnummer = personNrPasient
        tkNrFraDato = dateMinus1Year
        forespNr = 1.toBigInteger()
        forespTidsStempel = LocalDateTime.now()
        fraDato = dateMinus1Year
        eldsteFraDato = dateMinus4Years
        hovedDiagnosekode = healthInformation.medisinskVurdering.hovedDiagnose.diagnosekode.v
        hovedDiagnosekodeverk =
            Diagnosekode.values()
                .first {
                    it.kithCode == healthInformation.medisinskVurdering.hovedDiagnose.diagnosekode.s
                }
                .infotrygdCode
        fodselsnrBehandler = doctorFnr
        if (
            healthInformation.medisinskVurdering.biDiagnoser?.diagnosekode?.firstOrNull()?.v !=
                null &&
                healthInformation.medisinskVurdering.biDiagnoser?.diagnosekode?.firstOrNull()?.s !=
                    null
        ) {
            biDiagnoseKode = healthInformation.medisinskVurdering.biDiagnoser.diagnosekode.first().v
            biDiagnosekodeverk =
                Diagnosekode.values()
                    .first {
                        it.kithCode ==
                            healthInformation.medisinskVurdering.biDiagnoser.diagnosekode.first().s
                    }
                    .infotrygdCode
        }
        tkNrFraDato = dateMinus1Year
    }

fun finnLegeFnrFra(receivedSykmelding: ReceivedSykmelding): String {
    return if (receivedSykmelding.sykmelding.avsenderSystem.navn == "Egenmeldt") {
        // Testfamilien Aremark stepper inn som lege for egenmeldte sykmeldinger
        log.info(
            "Setter Aremark som lege for egenmeldt sykmelding med id {}",
            receivedSykmelding.sykmelding.id,
        )
        "10108000398"
    } else if (receivedSykmelding.erUtenlandskSykmelding()) {
        log.info(
            "Setter standardverdi for behandler for utenlandsk sykmelding med id ${receivedSykmelding.sykmelding.id}",
        )
        UTENLANDSK_SYKEHUS
    } else {
        receivedSykmelding.personNrLege
    }
}

fun sendInfotrygdSporring(
    producer: MessageProducer,
    session: Session,
    infotrygdForesp: InfotrygdForesp,
    temporaryQueue: TemporaryQueue,
) =
    producer.send(
        session.createTextMessage().apply {
            text = infotrygdSporringMarshaller.toString(infotrygdForesp)
            jmsReplyTo = temporaryQueue
        },
    )

private fun stripNonValidXMLCharacters(infotrygdString: String): String {
    val out = StringBuffer(infotrygdString)
    for (i in 0 until out.length) {
        if (out[i].code == 0x1a) {
            out.setCharAt(i, '-')
        }
    }
    return out.toString()
}

private fun safeUnmarshal(inputMessageText: String, id: String): InfotrygdForesp {
    // Disable XXE
    try {
        return infotrygdForesp(inputMessageText)
    } catch (ex: Exception) {
        log.warn("Error parsing response for $id", ex)
        sikkerlogg.warn("error parsing this $inputMessageText for: $id")
    }
    log.info("trying again with valid xml, for: $id")
    val validXML = stripNonValidXMLCharacters(inputMessageText)
    return infotrygdForesp(validXML)
}

private fun infotrygdForesp(validXML: String): InfotrygdForesp {
    val spf: SAXParserFactory = SAXParserFactory.newInstance()
    spf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
    spf.isNamespaceAware = true

    val xmlSource: Source =
        SAXSource(
            spf.newSAXParser().xmlReader,
            InputSource(StringReader(validXML)),
        )
    return infotrygdSporringUnmarshaller.unmarshal(xmlSource) as InfotrygdForesp
}
