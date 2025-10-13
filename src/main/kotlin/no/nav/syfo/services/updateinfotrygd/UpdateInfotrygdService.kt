package no.nav.syfo.services.updateinfotrygd

import io.opentelemetry.instrumentation.annotations.WithSpan
import jakarta.jms.MessageProducer
import jakarta.jms.Session
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.delay
import net.logstash.logback.argument.StructuredArguments.fields
import no.nav.helse.eiFellesformat.XMLEIFellesformat
import no.nav.helse.sm2013.KontrollsystemBlokkType
import no.nav.syfo.InfotrygdForespAndHealthInformation
import no.nav.syfo.erUtenlandskSykmelding
import no.nav.syfo.get
import no.nav.syfo.log
import no.nav.syfo.model.sykmelding.ReceivedSykmelding
import no.nav.syfo.services.ValkeyService
import no.nav.syfo.services.sha256hashstring
import no.nav.syfo.util.LoggingMeta
import no.nav.syfo.util.xmlObjectWriter
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord

const val INFOTRYGD = "INFOTRYGD"

class UpdateInfotrygdService(
    private val kafkaAivenProducerReceivedSykmelding: KafkaProducer<String, ReceivedSykmelding>,
    private val retryTopic: String,
    private val valkeyService: ValkeyService,
) {

    @WithSpan
    suspend fun updateInfotrygd(
        producer: MessageProducer,
        session: Session,
        loggingMeta: LoggingMeta,
        itfh: InfotrygdForespAndHealthInformation,
        receivedSykmelding: ReceivedSykmelding,
        behandlerKode: String,
        navKontorNr: String,
        behandletAvManuell: Boolean,
        operasjonstypeAndFom: Pair<Operasjonstype, LocalDate>,
    ) {
        val perioder = itfh.healthInformation.aktivitet.periode.sortedBy { it.periodeFOMDato }
        val marshalledFellesformat = receivedSykmelding.fellesformat
        val personNrPasient = receivedSykmelding.personNrPasient
        val signaturDato = receivedSykmelding.sykmelding.signaturDato.toLocalDate()
        val tssid = receivedSykmelding.tssid
        val forsteFravaersDag = operasjonstypeAndFom.second
        log.info(
            "Fant første fraværsdag for sykmelding id ${receivedSykmelding.sykmelding.id} til $forsteFravaersDag"
        )
        val sha256String =
            sha256hashstring(
                createInfotrygdBlokk(
                    itfh = itfh,
                    periode = perioder.first(),
                    personNrPasient = personNrPasient,
                    signaturDato = LocalDate.of(2019, 1, 1),
                    helsepersonellKategoriVerdi = behandlerKode,
                    tssid = tssid,
                    loggingMeta = loggingMeta,
                    navKontorNr = navKontorNr,
                    navnArbeidsgiver =
                        findArbeidsKategori(itfh.healthInformation.arbeidsgiver?.navnArbeidsgiver),
                    behandletAvManuell = behandletAvManuell,
                    utenlandskSykmelding = receivedSykmelding.erUtenlandskSykmelding(),
                    operasjonstypeAndFom = operasjonstypeAndFom,
                ),
            )

        delay(100)
        val nyligInfotrygdOppdatering =
            valkeyService.oppdaterValkey(personNrPasient, personNrPasient, 4, loggingMeta)

        when {
            nyligInfotrygdOppdatering == null -> {
                delay(4_000)
                try {
                    val producerRecord =
                        ProducerRecord(
                            retryTopic,
                            receivedSykmelding.sykmelding.id,
                            receivedSykmelding,
                        )
                    kafkaAivenProducerReceivedSykmelding
                        .send(
                            producerRecord,
                        )
                        .get()
                    log.info("Melding sendt på retry topic {}", fields(loggingMeta))
                } catch (ex: Exception) {
                    log.error("Failed to send sykmelding to retrytopic {}", fields(loggingMeta))
                    throw ex
                }
            }
            else -> {
                val duplikatInfotrygdOppdatering =
                    valkeyService.oppdaterValkey(
                        sha256String,
                        sha256String,
                        TimeUnit.DAYS.toSeconds(60).toInt(),
                        loggingMeta,
                    )
                when {
                    duplikatInfotrygdOppdatering == null -> {
                        log.warn(
                            "Melding market som infotrygd duplikat oppdatering {}",
                            fields(loggingMeta),
                        )
                    }
                    else ->
                        try {
                            if (receivedSykmelding.erUtenlandskSykmelding()) {
                                log.info(
                                    "Klargjør til å oppdatere infotrygd der navKontorNr er {} og er dette en utenlandsk sykmelding: {}. \n med loggingmetadata: {} \n erAdresseUtland= {}",
                                    navKontorNr,
                                    receivedSykmelding.erUtenlandskSykmelding(),
                                    loggingMeta,
                                    receivedSykmelding.utenlandskSykmelding?.erAdresseUtland,
                                )
                            }
                            sendInfotrygdOppdateringMq(
                                producer,
                                session,
                                createInfotrygdFellesformat(
                                    marshalledFellesformat = marshalledFellesformat,
                                    itfh = itfh,
                                    periode = perioder.first(),
                                    personNrPasient = personNrPasient,
                                    signaturDato = signaturDato,
                                    helsepersonellKategoriVerdi = behandlerKode,
                                    tssid = tssid,
                                    loggingMeta = loggingMeta,
                                    navKontorNr = navKontorNr,
                                    identDato = forsteFravaersDag,
                                    behandletAvManuell = behandletAvManuell,
                                    utenlandskSykmelding =
                                        receivedSykmelding.erUtenlandskSykmelding(),
                                    operasjonstypeAndFom = operasjonstypeAndFom
                                ),
                                loggingMeta,
                            )
                            perioder.drop(1).forEach { periode ->
                                sendInfotrygdOppdateringMq(
                                    producer,
                                    session,
                                    createInfotrygdFellesformat(
                                        marshalledFellesformat = marshalledFellesformat,
                                        itfh = itfh,
                                        periode = periode,
                                        personNrPasient = personNrPasient,
                                        signaturDato = signaturDato,
                                        helsepersonellKategoriVerdi = behandlerKode,
                                        tssid = tssid,
                                        loggingMeta = loggingMeta,
                                        navKontorNr = navKontorNr,
                                        identDato = forsteFravaersDag,
                                        behandletAvManuell = behandletAvManuell,
                                        utenlandskSykmelding =
                                            receivedSykmelding.erUtenlandskSykmelding(),
                                        operasjonstypeAndFom =
                                            operasjonstypeAndFom.copy(
                                                first = Operasjonstype.ENDRING
                                            ),
                                    ),
                                    loggingMeta,
                                )
                            }
                        } catch (exception: Exception) {
                            valkeyService.slettValkeyKey(sha256String, loggingMeta)
                            log.error(
                                "Feilet i infotrygd oppdaternings biten, kaster exception",
                                exception,
                            )
                            throw exception
                        }
                }
            }
        }
    }

    @WithSpan
    private fun sendInfotrygdOppdateringMq(
        producer: MessageProducer,
        session: Session,
        fellesformat: XMLEIFellesformat,
        loggingMeta: LoggingMeta,
    ) =
        producer.send(
            session.createTextMessage().apply {
                log.info(
                    "Melding har oprasjonstype: {}, tkNummer: {}, {}",
                    fellesformat
                        .get<KontrollsystemBlokkType>()
                        .infotrygdBlokk
                        .first()
                        .operasjonstype,
                    fellesformat.get<KontrollsystemBlokkType>().infotrygdBlokk.first().tkNummer,
                    fields(loggingMeta),
                )
                text = xmlObjectWriter.writeValueAsString(fellesformat)
                log.info("Melding er sendt til infotrygd {}", fields(loggingMeta))
            },
        )
}
