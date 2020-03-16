package no.nav.syfo.services

import com.ctc.wstx.exc.WstxException
import java.io.IOException
import java.io.StringReader
import java.lang.IllegalStateException
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.jms.MessageProducer
import javax.jms.Session
import javax.jms.TemporaryQueue
import javax.jms.TextMessage
import no.nav.helse.tssSamhandlerData.XMLSamhandlerIDataB910Type
import no.nav.helse.tssSamhandlerData.XMLTServicerutiner
import no.nav.helse.tssSamhandlerData.XMLTidOFF1
import no.nav.helse.tssSamhandlerData.XMLTssSamhandlerData
import no.nav.syfo.helpers.retry
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.toString
import no.nav.syfo.util.tssSamhandlerdataInputMarshaller
import no.nav.syfo.util.tssSamhandlerdataUnmarshaller

suspend fun fetchTssSamhandlerInfo(
    receivedSykmelding: ReceivedSykmelding,
    tssSamhnadlerInfoProducer: MessageProducer,
    session: Session
): XMLTssSamhandlerData =
        retry(callName = "tss_hent_samhandler_data",
                retryIntervals = arrayOf(500L, 1000L, 3000L, 5000L, 10000L, 3600000L, 14400000L),
                legalExceptions = *arrayOf(IOException::class, WstxException::class, IllegalStateException::class)
        ) {
            val tssSamhandlerDatainput = XMLTssSamhandlerData().apply {
                tssInputData = XMLTssSamhandlerData.TssInputData().apply {
                    tssServiceRutine = XMLTServicerutiner().apply {
                        samhandlerIDataB960 = XMLSamhandlerIDataB910Type().apply {
                            ofFid = XMLTidOFF1().apply {
                                idOff = receivedSykmelding.personNrLege
                                kodeIdType = setFnrOrDnr(receivedSykmelding.personNrLege)
                            }
                            aktuellDato = formaterDato(LocalDate.now())
                            historikk = "J"
                        }
                    }
                }
            }

            val temporaryQueue = session.createTemporaryQueue()
            try {
                sendTssSporring(tssSamhnadlerInfoProducer, session, tssSamhandlerDatainput, temporaryQueue)
                session.createConsumer(temporaryQueue).use { tmpConsumer ->
                    val consumedMessage = tmpConsumer.receive(20000)
                    val inputMessageText = when (consumedMessage) {
                        is TextMessage -> consumedMessage.text
                        else -> throw RuntimeException("Incoming message needs to be a byte message or text message, JMS type:" + consumedMessage.jmsType)
                    }

                    tssSamhandlerdataUnmarshaller.unmarshal(StringReader(inputMessageText)) as XMLTssSamhandlerData
                }
            } finally {
                temporaryQueue.delete()
            }
        }

fun sendTssSporring(
    producer: MessageProducer,
    session: Session,
    tssSamhandlerData: XMLTssSamhandlerData,
    temporaryQueue: TemporaryQueue
) = producer.send(session.createTextMessage().apply {
    text = tssSamhandlerdataInputMarshaller.toString(tssSamhandlerData)
    jmsReplyTo = temporaryQueue
})

fun setFnrOrDnr(personNumber: String): String {
    return when (checkPersonNumberIsDnr(personNumber)) {
        true -> "DNR"
        else -> "FNR"
    }
}

fun checkPersonNumberIsDnr(personNumber: String): Boolean {
    val personNumberBornDay = personNumber.substring(0, 2)
    return validatePersonDNumberRange(personNumberBornDay)
}

fun validatePersonDNumberRange(personNumberFirstAndSecoundChar: String): Boolean {
    return personNumberFirstAndSecoundChar.toInt() in 41..71
}

fun formaterDato(dato: LocalDate): String {
    val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")
    return dato.format(formatter)
}
