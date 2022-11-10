package no.nav.syfo.services.updateinfotrygd

import kotlinx.coroutines.delay
import net.logstash.logback.argument.StructuredArguments
import net.logstash.logback.argument.StructuredArguments.fields
import no.nav.helse.eiFellesformat.XMLEIFellesformat
import no.nav.helse.infotrygd.foresp.InfotrygdForesp
import no.nav.helse.sm2013.HelseOpplysningerArbeidsuforhet
import no.nav.helse.sm2013.KontrollsystemBlokkType
import no.nav.syfo.InfotrygdForespAndHealthInformation
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.client.Behandler
import no.nav.syfo.client.Godkjenning
import no.nav.syfo.client.Kode
import no.nav.syfo.client.NorskHelsenettClient
import no.nav.syfo.get
import no.nav.syfo.log
import no.nav.syfo.metrics.RULE_HIT_COUNTER
import no.nav.syfo.metrics.RULE_HIT_STATUS_COUNTER
import no.nav.syfo.model.HelsepersonellKategori
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.model.RuleInfo
import no.nav.syfo.model.Status
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.services.BehandlingsutfallService
import no.nav.syfo.services.OppgaveService
import no.nav.syfo.services.RedisService
import no.nav.syfo.services.sha256hashstring
import no.nav.syfo.sortedFOMDate
import no.nav.syfo.util.LoggingMeta
import no.nav.syfo.util.xmlObjectWriter
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import redis.clients.jedis.exceptions.JedisConnectionException
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import javax.jms.MessageProducer
import javax.jms.Session

const val INFOTRYGD = "INFOTRYGD"

class UpdateInfotrygdService(
    private val norskHelsenettClient: NorskHelsenettClient,
    private val applicationState: ApplicationState,
    private val kafkaAivenProducerReceivedSykmelding: KafkaProducer<String, ReceivedSykmelding>,
    private val retryTopic: String,
    private val behandlingsutfallService: BehandlingsutfallService,
    private val redisService: RedisService,
    private val oppgaveService: OppgaveService
) {

    suspend fun updateInfotrygd(
        receivedSykmelding: ReceivedSykmelding,
        validationResult: ValidationResult,
        infotrygdOppdateringProducer: MessageProducer,
        navKontorLokalKontor: String,
        loggingMeta: LoggingMeta,
        session: Session,
        infotrygdForespResponse: InfotrygdForesp,
        healthInformation: HelseOpplysningerArbeidsuforhet,
        behandletAvManuell: Boolean
    ) {
        val helsepersonell = if (erEgenmeldt(receivedSykmelding) || receivedSykmelding.utenlandskSykmelding != null) {
            Behandler(listOf(Godkjenning(helsepersonellkategori = Kode(aktiv = true, oid = 0, verdi = HelsepersonellKategori.LEGE.verdi), autorisasjon = Kode(aktiv = true, oid = 0, verdi = ""))))
        } else {
            norskHelsenettClient.finnBehandler(receivedSykmelding.personNrLege, receivedSykmelding.msgId)
        }

        if (helsepersonell != null) {
            val helsepersonellKategoriVerdi = finnAktivHelsepersonellAutorisasjons(helsepersonell)
            when (validationResult.status) {
                in arrayOf(Status.MANUAL_PROCESSING) ->
                    produceManualTaskAndSendValidationResults(
                        receivedSykmelding, validationResult,
                        loggingMeta,
                        InfotrygdForespAndHealthInformation(infotrygdForespResponse, healthInformation),
                        helsepersonellKategoriVerdi,
                        behandletAvManuell
                    )
                else -> sendInfotrygdOppdateringAndValidationResult(
                    infotrygdOppdateringProducer,
                    session,
                    loggingMeta,
                    InfotrygdForespAndHealthInformation(infotrygdForespResponse, healthInformation),
                    receivedSykmelding,
                    helsepersonellKategoriVerdi,
                    navKontorLokalKontor,
                    validationResult,
                    behandletAvManuell
                )
            }

            log.info(
                "Message(${fields(loggingMeta)}) got outcome {}, {}, processing took {}s",
                StructuredArguments.keyValue("status", validationResult.status),
                StructuredArguments.keyValue("ruleHits", validationResult.ruleHits.joinToString(", ", "(", ")") { it.ruleName })
            )
        } else {
            val validationResultBehandler = ValidationResult(
                status = Status.MANUAL_PROCESSING,
                ruleHits = listOf(
                    RuleInfo(
                        ruleName = "BEHANDLER_NOT_IN_HPR",
                        messageForSender = "Den som har skrevet sykmeldingen din har ikke autorisasjon til dette.",
                        messageForUser = "Behandler er ikke registert i HPR",
                        ruleStatus = Status.MANUAL_PROCESSING
                    )
                )
            )
            RULE_HIT_STATUS_COUNTER.labels(validationResultBehandler.status.name).inc()
            RULE_HIT_COUNTER.labels(validationResultBehandler.ruleHits.first().ruleName).inc()
            log.warn("Behandler er ikke registert i HPR")
            produceManualTaskAndSendValidationResults(
                receivedSykmelding, validationResultBehandler,
                loggingMeta,
                InfotrygdForespAndHealthInformation(infotrygdForespResponse, healthInformation),
                HelsepersonellKategori.LEGE.verdi,
                behandletAvManuell
            )
        }
    }

    private suspend fun sendInfotrygdOppdateringAndValidationResult(
        producer: MessageProducer,
        session: Session,
        loggingMeta: LoggingMeta,
        itfh: InfotrygdForespAndHealthInformation,
        receivedSykmelding: ReceivedSykmelding,
        behandlerKode: String,
        navKontorNr: String,
        validationResult: ValidationResult,
        behandletAvManuell: Boolean
    ) {
        val perioder = itfh.healthInformation.aktivitet.periode.sortedBy { it.periodeFOMDato }
        val marshalledFellesformat = receivedSykmelding.fellesformat
        val personNrPasient = receivedSykmelding.personNrPasient
        val signaturDato = receivedSykmelding.sykmelding.signaturDato.toLocalDate()
        val tssid = receivedSykmelding.tssid

        val forsteFravaersDag = finnForsteFravaersDag(itfh, perioder.first(), loggingMeta)

        val sha256String = sha256hashstring(
            createInfotrygdBlokk(
                itfh, perioder.first(), personNrPasient, LocalDate.of(2019, 1, 1),
                behandlerKode, tssid, loggingMeta, navKontorNr, findArbeidsKategori(itfh.healthInformation.arbeidsgiver?.navnArbeidsgiver),
                forsteFravaersDag, behandletAvManuell
            )
        )

        delay(100)
        val nyligInfotrygdOppdatering = redisService.oppdaterRedis(personNrPasient, personNrPasient, 4, loggingMeta)

        when {
            nyligInfotrygdOppdatering == null -> {
                delay(10000)
                try {
                    kafkaAivenProducerReceivedSykmelding.send(
                        ProducerRecord(
                            retryTopic,
                            receivedSykmelding.sykmelding.id,
                            receivedSykmelding
                        )
                    ).get()
                    log.warn("Melding sendt på aiven retry topic {}", fields(loggingMeta))
                } catch (ex: Exception) {
                    log.error("Failed to send sykmelding to aiven retrytopic {}", fields(loggingMeta))
                    throw ex
                }
            }
            else -> {
                val duplikatInfotrygdOppdatering = redisService.oppdaterRedis(sha256String, sha256String, TimeUnit.DAYS.toSeconds(60).toInt(), loggingMeta)
                when {
                    duplikatInfotrygdOppdatering == null -> {
                        behandlingsutfallService.sendRuleCheckValidationResult(
                            receivedSykmelding,
                            validationResult,
                            loggingMeta
                        )
                        log.warn("Melding market som infotrygd duplikat oppdaatering {}", fields(loggingMeta))
                    }
                    else ->
                        try {
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
                                    behandletAvManuell = behandletAvManuell
                                ),
                                loggingMeta
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
                                        operasjonstypeKode = 2
                                    ),
                                    loggingMeta
                                )
                            }
                            behandlingsutfallService.sendRuleCheckValidationResult(
                                receivedSykmelding,
                                validationResult,
                                loggingMeta
                            )
                        } catch (exception: Exception) {
                            redisService.slettRedisKey(sha256String, loggingMeta)
                            log.error("Feilet i infotrygd oppdaternings biten, kaster exception", exception)
                            throw exception
                        }
                }
            }
        }
    }

    private fun sendInfotrygdOppdateringMq(
        producer: MessageProducer,
        session: Session,
        fellesformat: XMLEIFellesformat,
        loggingMeta: LoggingMeta
    ) = producer.send(
        session.createTextMessage().apply {
            log.info("Melding har oprasjonstype: {}, tkNummer: {}, {}", fellesformat.get<KontrollsystemBlokkType>().infotrygdBlokk.first().operasjonstype, fellesformat.get<KontrollsystemBlokkType>().infotrygdBlokk.first().tkNummer, fields(loggingMeta))
            text = xmlObjectWriter.writeValueAsString(fellesformat)
            log.info("Melding er sendt til infotrygd {}", fields(loggingMeta))
        }
    )

    private fun produceManualTaskAndSendValidationResults(
        receivedSykmelding: ReceivedSykmelding,
        validationResult: ValidationResult,
        loggingMeta: LoggingMeta,
        itfh: InfotrygdForespAndHealthInformation,
        helsepersonellKategoriVerdi: String,
        behandletAvManuell: Boolean
    ) {
        behandlingsutfallService.sendRuleCheckValidationResult(receivedSykmelding, validationResult, loggingMeta)
        try {
            val perioder = itfh.healthInformation.aktivitet.periode.sortedBy { it.periodeFOMDato }
            val forsteFravaersDag = itfh.healthInformation.aktivitet.periode.sortedFOMDate().first()
            val tssid = if (!receivedSykmelding.tssid.isNullOrBlank()) {
                receivedSykmelding.tssid
            } else {
                "0"
            }
            val sha256String = sha256hashstring(
                createInfotrygdBlokk(
                    itfh, perioder.first(), receivedSykmelding.personNrPasient, LocalDate.of(2019, 1, 1),
                    helsepersonellKategoriVerdi, tssid, loggingMeta, "",
                    findArbeidsKategori(itfh.healthInformation.arbeidsgiver?.navnArbeidsgiver),
                    forsteFravaersDag, behandletAvManuell, 1
                )
            )

            val duplikatInfotrygdOppdatering = redisService.erIRedis(sha256String)

            if (errorFromInfotrygd(validationResult.ruleHits)) {
                redisService.oppdaterAntallErrorIInfotrygd(INFOTRYGD, "1", TimeUnit.MINUTES.toSeconds(1).toInt(), loggingMeta)
            }

            val antallErrorFraInfotrygd = redisService.antallErrorIInfotrygd(INFOTRYGD, loggingMeta)

            if (antallErrorFraInfotrygd > 50) {
                log.error("Setter applicationState.ready til false")
                applicationState.ready = false
            }

            val skalIkkeOppdatereInfotrygd = skalIkkeProdusereManuellOppgave(receivedSykmelding, validationResult)

            when {
                duplikatInfotrygdOppdatering -> {
                    log.warn("Melding er infotrygd duplikat, ikke opprett manuelloppgave {}", fields(loggingMeta))
                }
                skalIkkeOppdatereInfotrygd -> {
                    log.warn("Trenger ikke å opprett manuell oppgave for {}", fields(loggingMeta))
                }
                else -> {
                    oppgaveService.opprettOppgave(receivedSykmelding, validationResult, behandletAvManuell, loggingMeta)
                    redisService.oppdaterRedis(sha256String, sha256String, TimeUnit.DAYS.toSeconds(60).toInt(), loggingMeta)
                }
            }
        } catch (connectionException: JedisConnectionException) {
            log.error("Fikk ikkje opprettet kontakt med redis, kaster exception", connectionException)
            throw connectionException
        }
    }

    private fun erEgenmeldt(receivedSykmelding: ReceivedSykmelding): Boolean =
        receivedSykmelding.sykmelding.avsenderSystem.navn == "Egenmeldt"
}
