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
import no.nav.syfo.NAV_OPPFOLGING_UTLAND_KONTOR_NR
import no.nav.syfo.UTENLANDSK_SYKEHUS
import no.nav.syfo.erUtenlandskSykmelding
import no.nav.syfo.helpers.retry
import no.nav.syfo.log
import no.nav.syfo.model.Diagnosekode
import no.nav.syfo.model.sykmelding.ReceivedSykmelding
import no.nav.syfo.objectMapper
import no.nav.syfo.toString
import no.nav.syfo.util.infotrygdSporringJaxBContext
import org.xml.sax.InputSource

@WithSpan
suspend fun fetchInfotrygdForesp(
    receivedSykmelding: ReceivedSykmelding,
    infotrygdSporringProducer: MessageProducer,
    session: Session,
    healthInformation: HelseOpplysningerArbeidsuforhet,
    navkontor: String,
): InfotrygdForesp =
    retry(
        callName = "it_hent_infotrygdForesp",
        retryIntervals = arrayOf(500L, 1000L, 3000L, 5000L, 10000L),
        legalExceptions =
            arrayOf(IOException::class, WstxException::class, IllegalStateException::class),
    ) {
        val utlandKontor =
            if (navkontor == NAV_OPPFOLGING_UTLAND_KONTOR_NR) {
                navkontor
            } else null

        val infotrygdForespRequest =
            createInfotrygdForesp(
                receivedSykmelding.personNrPasient,
                healthInformation.toInfotrygdForespValues(),
                finnLegeFnrFra(receivedSykmelding),
                utlandKontor,
            )
        sendInfotrygdForesporsel(
            session,
            infotrygdSporringProducer,
            infotrygdForespRequest,
            receivedSykmelding.sykmelding.id,
        )
    }

fun sendInfotrygdForesporsel(
    session: Session,
    infotrygdSporringProducer: MessageProducer,
    infotrygdForespRequest: InfotrygdForesp,
    id: String,
): InfotrygdForesp {
    val temporaryQueue = session.createTemporaryQueue()
    return try {
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
                    null -> throw RuntimeException("Incoming message is null")
                    is TextMessage -> consumedMessage.text
                    else ->
                        throw RuntimeException(
                            "Incoming message needs to be a byte message or text message, JMS type: $consumedMessage.jmsType",
                        )
                }
            safeUnmarshal(inputMessageText, id, infotrygdForespRequest.tkNummer).also {
                sikkerlogg.info(
                    "infotrygdForespResponse: ${
                        objectMapper.writeValueAsString(
                            it,
                        )
                    }" +
                        " for $id",
                )
            }
        }
    } finally {
        temporaryQueue.delete()
    }
}

class InfotrygdForespValues(
    val hovedDiagnosekode: String?,
    val hovedDiagnosekodeverk: String?,
    val biDiagnoseKode: String?,
    val biDiagnosekodeverk: String?,
)

fun HelseOpplysningerArbeidsuforhet.toInfotrygdForespValues(): InfotrygdForespValues {
    return InfotrygdForespValues(
        hovedDiagnosekode = medisinskVurdering.hovedDiagnose.diagnosekode.v,
        hovedDiagnosekodeverk =
            Diagnosekode.entries
                .first { it.kithCode == medisinskVurdering.hovedDiagnose.diagnosekode.s }
                .infotrygdCode,
        biDiagnoseKode = medisinskVurdering.biDiagnoser?.diagnosekode?.firstOrNull()?.v,
        biDiagnosekodeverk =
            medisinskVurdering.biDiagnoser?.diagnosekode?.firstOrNull()?.s?.let { system ->
                Diagnosekode.entries.first { it.kithCode == system }.infotrygdCode
            },
    )
}

fun createInfotrygdForesp(
    personNrPasient: String,
    infotrygdForespValues: InfotrygdForespValues,
    doctorFnr: String?,
    navKontor: String?,
) =
    InfotrygdForesp().apply {
        val dateMinus1Year = LocalDate.now().minusYears(1)
        val dateMinus4Years = LocalDate.now().minusYears(4)
        tkNummer = navKontor
        fodselsnummer = personNrPasient
        tkNrFraDato = dateMinus1Year
        forespNr = 1.toBigInteger()
        forespTidsStempel = LocalDateTime.now()
        fraDato = dateMinus1Year
        eldsteFraDato = dateMinus4Years
        hovedDiagnosekode = infotrygdForespValues.hovedDiagnosekode
        hovedDiagnosekodeverk = infotrygdForespValues.hovedDiagnosekodeverk
        fodselsnrBehandler = doctorFnr
        if (
            infotrygdForespValues.biDiagnoseKode != null &&
            infotrygdForespValues.biDiagnosekodeverk != null
        ) {
            biDiagnoseKode = infotrygdForespValues.biDiagnoseKode
            biDiagnosekodeverk = infotrygdForespValues.biDiagnosekodeverk
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
) {
    val infotrygdForespJson = objectMapper.writeValueAsString(infotrygdForesp)
    sikkerlogg.info("sending infotrygdforsp json: $infotrygdForespJson")
    val infotrygdForespXml =
        infotrygdSporringJaxBContext.createMarshaller().toString(infotrygdForesp)
    sikkerlogg.info("sending infotrygdforsp xml: $infotrygdForespXml")

    producer.send(
        session.createTextMessage().apply {
            text = infotrygdForespXml
            jmsReplyTo = temporaryQueue
        },
    )
}

private fun stripNonValidXMLCharacters(
    infotrygdString: String,
    id: String,
    navkontor: String?
): String {
    val out = StringBuffer(infotrygdString)
    for (i in out.length - 1 downTo  0) {
        if (out[i].code == 0x1c && id == "bd340ba3-a3ee-4545-9175-d43316d4a51b") {
            out.deleteCharAt(i)
        }
        if (out[i].code == 0x1a) {
            out.setCharAt(i, '-')
        } else if (navkontor == NAV_OPPFOLGING_UTLAND_KONTOR_NR && out[i].code == 0x0) {
            log.info("We have 0x0 char in xml for $navkontor, replacing with space for $id")
            out.setCharAt(i, ' ')
        }
    }
    return out.toString()
}

fun safeUnmarshal(inputMessageText: String, id: String, navkontor: String?): InfotrygdForesp {
    // Disable XXE
    try {
        return infotrygdForesp(inputMessageText)
    } catch (ex: Exception) {
        log.warn("Error parsing response for $id", ex)
        sikkerlogg.warn("error parsing this $inputMessageText for: $id")
    }
    log.info("trying again with valid xml, for: $id")
    val validXML = stripNonValidXMLCharacters(
        infotrygdString = inputMessageText,
        id = id,
        navkontor = navkontor,
    )

    val result = infotrygdForesp(validXML)

    return result
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
    return infotrygdSporringJaxBContext.createUnmarshaller().unmarshal(xmlSource) as InfotrygdForesp
}
