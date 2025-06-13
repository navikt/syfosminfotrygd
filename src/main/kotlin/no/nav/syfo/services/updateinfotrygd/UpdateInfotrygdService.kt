package no.nav.syfo.services.updateinfotrygd

import io.opentelemetry.instrumentation.annotations.WithSpan
import jakarta.jms.MessageProducer
import jakarta.jms.Session
import net.logstash.logback.argument.StructuredArguments.fields
import no.nav.helse.eiFellesformat.XMLEIFellesformat
import no.nav.helse.sm2013.KontrollsystemBlokkType
import no.nav.syfo.InfotrygdForespAndHealthInformation
import no.nav.syfo.erUtenlandskSykmelding
import no.nav.syfo.get
import no.nav.syfo.log
import no.nav.syfo.model.sykmelding.ReceivedSykmelding
import no.nav.syfo.model.sykmelding.ValidationResult
import no.nav.syfo.services.BehandlingsutfallService
import no.nav.syfo.services.ValkeyService
import no.nav.syfo.util.LoggingMeta
import no.nav.syfo.util.xmlObjectWriter
import org.apache.kafka.clients.producer.KafkaProducer

const val INFOTRYGD = "INFOTRYGD"

class UpdateInfotrygdService(
    private val kafkaAivenProducerReceivedSykmelding: KafkaProducer<String, ReceivedSykmelding>,
    private val retryTopic: String,
    private val behandlingsutfallService: BehandlingsutfallService,
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
        validationResult: ValidationResult,
        behandletAvManuell: Boolean,
        tsmProcessingtarget: Boolean,
    ) {
        val perioder = itfh.healthInformation.aktivitet.periode.sortedBy { it.periodeFOMDato }
        val marshalledFellesformat = receivedSykmelding.fellesformat
        val personNrPasient = receivedSykmelding.personNrPasient
        val signaturDato = receivedSykmelding.sykmelding.signaturDato.toLocalDate()
        val tssid = receivedSykmelding.tssid

        val forsteFravaersDag = finnForsteFravaersDag(itfh, perioder.first(), loggingMeta)
        log.info(
            "Fant første fraværsdag for sykmelding id ${receivedSykmelding.sykmelding.id} til $forsteFravaersDag"
        )
        /* val sha256String =
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
                            identDato = forsteFravaersDag,
                            behandletAvManuell = behandletAvManuell,
                            utenlandskSykmelding = receivedSykmelding.erUtenlandskSykmelding(),
                        ),
                    )

                delay(100)
                val nyligInfotrygdOppdatering =
                    valkeyService.oppdaterValkey(personNrPasient, personNrPasient, 4, loggingMeta)
        */
        when {
            /* nyligInfotrygdOppdatering == null -> {
                delay(10_000)
                try {
                    val producerRecord =
                        ProducerRecord(
                            retryTopic,
                            receivedSykmelding.sykmelding.id,
                            receivedSykmelding,
                        )
                    if (tsmProcessingtarget) {
                        log.info(
                            "adding processingtarget to retrytopic for sykmeldingId: ${receivedSykmelding.sykmelding.id}"
                        )
                        producerRecord
                            .headers()
                            .add(
                                PROCESSING_TARGET_HEADER,
                                TSM_PROCESSING_TARGET_VALUE.toByteArray(Charsets.UTF_8)
                            )
                    }
                    kafkaAivenProducerReceivedSykmelding
                        .send(
                            producerRecord,
                        )
                        .get()
                    log.warn("Melding sendt på retry topic {}", fields(loggingMeta))
                } catch (ex: Exception) {
                    log.error("Failed to send sykmelding to retrytopic {}", fields(loggingMeta))
                    throw ex
                }
            }*/
            else -> {
                /*   val duplikatInfotrygdOppdatering =
                valkeyService.oppdaterValkey(
                    sha256String,
                    sha256String,
                    TimeUnit.DAYS.toSeconds(60).toInt(),
                    loggingMeta,
                )*/
                when {
                    /*duplikatInfotrygdOppdatering == null -> {
                        behandlingsutfallService.sendRuleCheckValidationResult(
                            receivedSykmelding.sykmelding.id,
                            validationResult,
                            loggingMeta,
                            tsmProcessingtarget
                        )
                        log.warn(
                            "Melding market som infotrygd duplikat oppdatering {}",
                            fields(loggingMeta),
                        )
                    }*/
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
                                        operasjonstypeKode = 2,
                                    ),
                                    loggingMeta,
                                )
                            }
                            behandlingsutfallService.sendRuleCheckValidationResult(
                                receivedSykmelding.sykmelding.id,
                                validationResult,
                                loggingMeta,
                                tsmProcessingtarget
                            )
                        } catch (exception: Exception) {
                            // valkeyService.slettValkeyKey(sha256String, loggingMeta)
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
