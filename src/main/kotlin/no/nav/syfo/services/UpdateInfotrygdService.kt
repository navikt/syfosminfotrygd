package no.nav.syfo.services

import io.ktor.util.KtorExperimentalAPI
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import javax.jms.MessageProducer
import javax.jms.Session
import kotlin.math.absoluteValue
import kotlinx.coroutines.delay
import net.logstash.logback.argument.StructuredArguments
import no.nav.helse.eiFellesformat.XMLEIFellesformat
import no.nav.helse.infotrygd.foresp.InfotrygdForesp
import no.nav.helse.infotrygd.foresp.TypeSMinfo
import no.nav.helse.sm2013.HelseOpplysningerArbeidsuforhet
import no.nav.helse.sm2013.KontrollSystemBlokk
import no.nav.helse.sm2013.KontrollsystemBlokkType
import no.nav.syfo.InfotrygdForespAndHealthInformation
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.client.Behandler
import no.nav.syfo.client.NorskHelsenettClient
import no.nav.syfo.daysBetween
import no.nav.syfo.get
import no.nav.syfo.log
import no.nav.syfo.metrics.MANUELLE_OPPGAVER_COUNTER
import no.nav.syfo.metrics.RULE_HIT_STATUS_COUNTER
import no.nav.syfo.model.HelsepersonellKategori
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.model.RuleInfo
import no.nav.syfo.model.Status
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.rules.ValidationRuleChain
import no.nav.syfo.rules.sortedPeriodeFOMDate
import no.nav.syfo.rules.sortedPeriodeTOMDate
import no.nav.syfo.rules.sortedSMInfos
import no.nav.syfo.sak.avro.PrioritetType
import no.nav.syfo.sak.avro.ProduceTask
import no.nav.syfo.sendRuleCheckValidationResult
import no.nav.syfo.sortedFOMDate
import no.nav.syfo.unmarshal
import no.nav.syfo.util.LoggingMeta
import no.nav.syfo.util.xmlObjectWriter
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import redis.clients.jedis.Jedis
import redis.clients.jedis.exceptions.JedisConnectionException

const val INFOTRYGD = "INFOTRYGD"

@KtorExperimentalAPI
class UpdateInfotrygdService {

    suspend fun updateInfotrygd(
        receivedSykmelding: ReceivedSykmelding,
        norskHelsenettClient: NorskHelsenettClient,
        validationResult: ValidationResult,
        infotrygdOppdateringProducer: MessageProducer,
        kafkaproducerCreateTask: KafkaProducer<String, ProduceTask>,
        navKontorManuellOppgave: String,
        navKontorLokalKontor: String,
        loggingMeta: LoggingMeta,
        session: Session,
        infotrygdForespResponse: InfotrygdForesp,
        healthInformation: HelseOpplysningerArbeidsuforhet,
        jedis: Jedis,
        kafkaproducerreceivedSykmelding: KafkaProducer<String, ReceivedSykmelding>,
        infotrygdRetryTopic: String,
        oppgaveTopic: String,
        kafkaproducervalidationResult: KafkaProducer<String, ValidationResult>,
        sm2013BehandlingsUtfallToipic: String,
        applicationState: ApplicationState
    ) {
        val helsepersonell = norskHelsenettClient.finnBehandler(receivedSykmelding.personNrLege, receivedSykmelding.msgId)

            if (helsepersonell != null) {
                val helsepersonellKategoriVerdi = finnAktivHelsepersonellAutorisasjons(helsepersonell)
                when {
                    validationResult.status in arrayOf(Status.MANUAL_PROCESSING) ->
                        produceManualTaskAndSendValidationResults(kafkaproducerCreateTask, receivedSykmelding, validationResult,
                                navKontorManuellOppgave, loggingMeta, oppgaveTopic, sm2013BehandlingsUtfallToipic,
                                kafkaproducervalidationResult,
                                InfotrygdForespAndHealthInformation(infotrygdForespResponse, healthInformation),
                                helsepersonellKategoriVerdi, jedis, applicationState)
                    else -> sendInfotrygdOppdateringAndValidationResult(
                            infotrygdOppdateringProducer,
                            session,
                            loggingMeta,
                            InfotrygdForespAndHealthInformation(infotrygdForespResponse, healthInformation),
                            receivedSykmelding,
                            helsepersonellKategoriVerdi,
                            navKontorLokalKontor,
                            jedis,
                            kafkaproducerreceivedSykmelding,
                            infotrygdRetryTopic,
                            kafkaproducervalidationResult,
                            sm2013BehandlingsUtfallToipic,
                            validationResult)
                }

                log.info("Message(${StructuredArguments.fields(loggingMeta)}) got outcome {}, {}, processing took {}s",
                        StructuredArguments.keyValue("status", validationResult.status),
                        StructuredArguments.keyValue("ruleHits", validationResult.ruleHits.joinToString(", ", "(", ")") { it.ruleName }))
            } else {
                val validationResultBehandler = ValidationResult(
                        status = Status.MANUAL_PROCESSING,
                        ruleHits = listOf(RuleInfo(
                                ruleName = "BEHANDLER_NOT_IN_HPR",
                                messageForSender = "Den som har skrevet sykmeldingen din har ikke autorisasjon til dette.",
                                messageForUser = "Behandler er ikke register i HPR",
                                ruleStatus = Status.MANUAL_PROCESSING))
                )
                RULE_HIT_STATUS_COUNTER.labels(validationResultBehandler.status.name).inc()
                log.warn("Behandler er ikke register i HPR")
                produceManualTaskAndSendValidationResults(kafkaproducerCreateTask, receivedSykmelding, validationResultBehandler,
                        navKontorManuellOppgave, loggingMeta, oppgaveTopic, sm2013BehandlingsUtfallToipic, kafkaproducervalidationResult,
                        InfotrygdForespAndHealthInformation(infotrygdForespResponse, healthInformation),
                        HelsepersonellKategori.LEGE.verdi, jedis, applicationState)
            }
    }

suspend fun sendInfotrygdOppdateringAndValidationResult(
    producer: MessageProducer,
    session: Session,
    loggingMeta: LoggingMeta,
    itfh: InfotrygdForespAndHealthInformation,
    receivedSykmelding: ReceivedSykmelding,
    behandlerKode: String,
    navKontorNr: String,
    jedis: Jedis,
    kafkaproducerreceivedSykmelding: KafkaProducer<String, ReceivedSykmelding>,
    infotrygdRetryTopic: String,
    kafkaproducervalidationResult: KafkaProducer<String, ValidationResult>,
    sm2013BehandlingsUtfallToipic: String,
    validationResult: ValidationResult
) {
        val perioder = itfh.healthInformation.aktivitet.periode.sortedBy { it.periodeFOMDato }
        val marshalledFellesformat = receivedSykmelding.fellesformat
        val personNrPasient = receivedSykmelding.personNrPasient
        val signaturDato = receivedSykmelding.sykmelding.signaturDato.toLocalDate()
        val tssid = receivedSykmelding.tssid

        val forsteFravaersDag = finnForsteFravaersDag(itfh, perioder.first(), loggingMeta)

        val sha256String = sha256hashstring(createInfotrygdBlokk(
                itfh, perioder.first(), personNrPasient, LocalDate.of(2019, 1, 1),
                behandlerKode, tssid, loggingMeta, navKontorNr, findarbeidsKategori(itfh.healthInformation.arbeidsgiver?.navnArbeidsgiver), forsteFravaersDag)
        )

        try {
            delay(100)
            val nyligInfotrygdOppdatering = erIRedis(personNrPasient, jedis)
            val duplikatInfotrygdOppdatering = erIRedis(sha256String, jedis)

            when {
                nyligInfotrygdOppdatering -> {
                    delay(10000)
                    kafkaproducerreceivedSykmelding.send(ProducerRecord(infotrygdRetryTopic, receivedSykmelding.sykmelding.id, receivedSykmelding))
                    log.warn("Melding sendt på retry topic {}", StructuredArguments.fields(loggingMeta))
                }
                duplikatInfotrygdOppdatering -> log.warn("Melding market som infotrygd duplikat oppdaatering {}", StructuredArguments.fields(loggingMeta))
                else -> {
                    oppdaterRedis(personNrPasient, personNrPasient, jedis, 4, loggingMeta)
                    oppdaterRedis(sha256String, sha256String, jedis, TimeUnit.DAYS.toSeconds(60).toInt(), loggingMeta)
                    sendInfotrygdOppdateringMq(producer, session, createInfotrygdFellesformat(marshalledFellesformat, itfh, perioder.first(), personNrPasient, signaturDato, behandlerKode, tssid, loggingMeta, navKontorNr, forsteFravaersDag), loggingMeta)
                    perioder.drop(1).forEach { periode ->
                        sendInfotrygdOppdateringMq(producer, session, createInfotrygdFellesformat(marshalledFellesformat, itfh, periode, personNrPasient, signaturDato, behandlerKode, tssid, loggingMeta, navKontorNr, forsteFravaersDag, 2), loggingMeta)
                    }
                    sendRuleCheckValidationResult(
                            receivedSykmelding,
                            kafkaproducervalidationResult,
                            validationResult,
                            sm2013BehandlingsUtfallToipic,
                            loggingMeta)
                }
            }
        } catch (connectionException: JedisConnectionException) {
            log.error("Fikk ikkje opprettet kontakt med redis, kaster exception", connectionException)
            throw connectionException
        }
    }

    fun createInfotrygdBlokk(
        itfh: InfotrygdForespAndHealthInformation,
        periode: HelseOpplysningerArbeidsuforhet.Aktivitet.Periode,
        personNrPasient: String,
        signaturDato: LocalDate,
        helsepersonellKategoriVerdi: String,
        tssid: String?,
        loggingMeta: LoggingMeta,
        navKontorNr: String,
        navnArbeidsgiver: String?,
        identDato: LocalDate,
        operasjonstypeKode: Int = findOperasjonstype(periode, itfh, loggingMeta)
    ) = KontrollsystemBlokkType.InfotrygdBlokk().apply {
        fodselsnummer = personNrPasient
        tkNummer = navKontorNr

        operasjonstype = operasjonstypeKode.toBigInteger()

        val typeSMinfo = itfh.infotrygdForesp.sMhistorikk?.sykmelding
                ?.sortedSMInfos()
                ?.lastOrNull()

        if ((typeSMinfo != null && tssid?.toBigInteger() != typeSMinfo.periode.legeInstNr) || operasjonstype == 1.toBigInteger()) {
            legeEllerInstitusjonsNummer = tssid?.toBigInteger() ?: "0".toBigInteger()
            legeEllerInstitusjon = if (itfh.healthInformation.behandler != null) {
                itfh.healthInformation.behandler.formatName()
            } else {
                ""
            }
        }

        forsteFravaersDag = identDato

        mottakerKode = helsepersonellKategoriVerdi

        if (itfh.infotrygdForesp.diagnosekodeOK != null) {
            hovedDiagnose = itfh.infotrygdForesp.hovedDiagnosekode
            hovedDiagnoseGruppe = itfh.infotrygdForesp.hovedDiagnosekodeverk.toBigInteger()
            hovedDiagnoseTekst = itfh.infotrygdForesp.diagnosekodeOK.diagnoseTekst
        }

        if (operasjonstype == 1.toBigInteger()) {
            behandlingsDato = findbBehandlingsDato(itfh, signaturDato)

            arbeidsKategori = findarbeidsKategori(navnArbeidsgiver)
            gruppe = "96"
            saksbehandler = "Auto"

            if (itfh.infotrygdForesp.biDiagnosekodeverk != null &&
                    itfh.healthInformation.medisinskVurdering.biDiagnoser.diagnosekode.firstOrNull()?.dn != null &&
                    itfh.infotrygdForesp.diagnosekodeOK != null) {
                biDiagnose = itfh.infotrygdForesp.biDiagnoseKode
                biDiagnoseGruppe = itfh.infotrygdForesp.biDiagnosekodeverk.toBigInteger()
                biDiagnoseTekst = itfh.infotrygdForesp.diagnosekodeOK.bidiagnoseTekst
            }
        }

        if (itfh.healthInformation.medisinskVurdering?.isSvangerskap != null &&
                itfh.healthInformation.medisinskVurdering.isSvangerskap) {
            isErSvangerskapsrelatert = true
        }

        arbeidsufoerTOM = periode.periodeTOMDato
        ufoeregrad = when {
            periode.gradertSykmelding != null -> periode.gradertSykmelding.sykmeldingsgrad.toBigInteger()
            periode.aktivitetIkkeMulig != null -> 100.toBigInteger()
            else -> 0.toBigInteger()
        }
    }

    fun findOperasjonstype(
        periode: HelseOpplysningerArbeidsuforhet.Aktivitet.Periode,
        itfh: InfotrygdForespAndHealthInformation,
        loggingMeta: LoggingMeta
    ): Int {
        // FORSTEGANGS = 1, PAFOLGENDE = 2, ENDRING = 3
        val typeSMinfo = itfh.infotrygdForesp.sMhistorikk?.sykmelding
                ?.sortedSMInfos()
                ?.lastOrNull()
                ?: return 1

        return if (endringSykmelding(periode, itfh, typeSMinfo)) {
            3
        } else if (paafolgendeSykmelding(periode, itfh, typeSMinfo)) {
            2
        } else if (forstegangsSykmelding(periode, itfh, typeSMinfo)) {
            1
        } else {
            log.error("Could not determined operasjonstype {}", StructuredArguments.fields(loggingMeta))
            throw RuntimeException("Could not determined operasjonstype")
        }
    }

    fun forstegangsSykmelding(
        periode: HelseOpplysningerArbeidsuforhet.Aktivitet.Periode,
        itfh: InfotrygdForespAndHealthInformation,
        typeSMinfo: TypeSMinfo
    ): Boolean =
            itfh.infotrygdForesp.sMhistorikk.status.kodeMelding == "04" ||
                    (typeSMinfo.periode.arbufoerTOM != null && (typeSMinfo.periode.arbufoerTOM..periode.periodeFOMDato).daysBetween().absoluteValue >= 1)

    fun paafolgendeSykmelding(
        periode: HelseOpplysningerArbeidsuforhet.Aktivitet.Periode,
        itfh: InfotrygdForespAndHealthInformation,
        typeSMinfo: TypeSMinfo
    ): Boolean =
            itfh.infotrygdForesp.sMhistorikk.status.kodeMelding != "04" &&
                    periode.periodeFOMDato.isEqual(typeSMinfo.periode.arbufoerTOM) ||
                    ((periode.periodeFOMDato.isAfter(typeSMinfo.periode.arbufoerTOM) &&
                            (typeSMinfo.periode.arbufoerTOM..periode.periodeFOMDato).daysBetween() <= 1))

    fun endringSykmelding(
        periode: HelseOpplysningerArbeidsuforhet.Aktivitet.Periode,
        itfh: InfotrygdForespAndHealthInformation,
        typeSMinfo: TypeSMinfo
    ): Boolean =
            itfh.infotrygdForesp.sMhistorikk.status.kodeMelding != "04" &&
                    (typeSMinfo.periode.arbufoerFOM == periode.periodeFOMDato ||
                            (typeSMinfo.periode.arbufoerFOM.isBefore(periode.periodeFOMDato)) ||
                            (typeSMinfo.periode.arbufoerFOM != null &&
                                    sammePeriodeInfotrygd(typeSMinfo.periode, periode))) &&
                    !(typeSMinfo.periode.arbufoerTOM == null && (typeSMinfo.periode.arbufoerFOM..periode.periodeFOMDato).daysBetween() > 1) &&
                    !(periode.periodeFOMDato.isEqual(typeSMinfo.periode.arbufoerTOM)) &&
                    !(periode.periodeFOMDato.isAfter(typeSMinfo.periode.arbufoerTOM))

    fun findarbeidsKategori(navnArbeidsgiver: String?): String {
        return if (navnArbeidsgiver == null || navnArbeidsgiver.isBlank() || navnArbeidsgiver.isEmpty()) {
            "030"
        } else {
            "01"
        }
    }

    fun createInfotrygdFellesformat(
        marshalledFellesformat: String,
        itfh: InfotrygdForespAndHealthInformation,
        periode: HelseOpplysningerArbeidsuforhet.Aktivitet.Periode,
        personNrPasient: String,
        signaturDato: LocalDate,
        helsepersonellKategoriVerdi: String,
        tssid: String?,
        loggingMeta: LoggingMeta,
        navKontorNr: String,
        identDato: LocalDate,
        operasjonstypeKode: Int = findOperasjonstype(periode, itfh, loggingMeta)
    ) = unmarshal<XMLEIFellesformat>(marshalledFellesformat).apply {
        any.add(KontrollSystemBlokk().apply {
            infotrygdBlokk.add(createInfotrygdBlokk(
                    itfh,
                    periode,
                    personNrPasient,
                    signaturDato,
                    helsepersonellKategoriVerdi,
                    tssid,
                    loggingMeta,
                    navKontorNr,
                    itfh.healthInformation.arbeidsgiver?.navnArbeidsgiver,
                    identDato,
                    operasjonstypeKode))
        })
    }

    fun findbBehandlingsDato(itfh: InfotrygdForespAndHealthInformation, signaturDato: LocalDate): LocalDate {
        return if (itfh.healthInformation.kontaktMedPasient?.kontaktDato != null &&
                itfh.healthInformation.kontaktMedPasient?.behandletDato != null) {
            listOf(itfh.healthInformation.kontaktMedPasient.kontaktDato,
                    itfh.healthInformation.kontaktMedPasient.behandletDato.toLocalDate()).sorted().first()
        } else if (itfh.healthInformation.kontaktMedPasient?.behandletDato != null) {
            itfh.healthInformation.kontaktMedPasient.behandletDato.toLocalDate()
        } else {
            signaturDato
        }
    }

    fun finnAktivHelsepersonellAutorisasjons(helsepersonelPerson: Behandler): String =
            helsepersonelPerson.godkjenninger.firstOrNull {
                it.helsepersonellkategori?.aktiv != null &&
                        it.autorisasjon?.aktiv == true &&
                        it.helsepersonellkategori.verdi != null
            }?.helsepersonellkategori?.verdi ?: ""

    fun HelseOpplysningerArbeidsuforhet.Behandler.formatName(): String =
            if (navn.mellomnavn == null) {
                "${navn.etternavn.toUpperCase()} ${navn.fornavn.toUpperCase()}"
            } else {
                "${navn.etternavn.toUpperCase()} ${navn.fornavn.toUpperCase()} ${navn.mellomnavn.toUpperCase()}"
            }

    fun sammePeriodeInfotrygd(infotrygdPeriode: TypeSMinfo.Periode, sykemldingsPeriode: HelseOpplysningerArbeidsuforhet.Aktivitet.Periode): Boolean {
        return infotrygdPeriode.arbufoerFOM == sykemldingsPeriode.periodeFOMDato && infotrygdPeriode.arbufoerTOM == sykemldingsPeriode.periodeTOMDato
    }

    fun sendInfotrygdOppdateringMq(
        producer: MessageProducer,
        session: Session,
        fellesformat: XMLEIFellesformat,
        loggingMeta: LoggingMeta
    ) = producer.send(session.createTextMessage().apply {
        log.info("Melding har oprasjonstype: {}, tkNummer: {}, {}", fellesformat.get<KontrollsystemBlokkType>().infotrygdBlokk.first().operasjonstype, fellesformat.get<KontrollsystemBlokkType>().infotrygdBlokk.first().tkNummer, StructuredArguments.fields(loggingMeta))
        text = xmlObjectWriter.writeValueAsString(fellesformat)
        log.info("Melding er sendt til infotrygd {}", StructuredArguments.fields(loggingMeta))
    })

    fun finnForsteFravaersDag(
        itfh: InfotrygdForespAndHealthInformation,
        forstePeriode: HelseOpplysningerArbeidsuforhet.Aktivitet.Periode,
        loggingMeta: LoggingMeta
    ): LocalDate {
        val typeSMinfo = itfh.infotrygdForesp.sMhistorikk?.sykmelding
                ?.sortedSMInfos()
                ?.lastOrNull()
        return if (findOperasjonstype(forstePeriode, itfh, loggingMeta) == 1) {
            itfh.healthInformation.aktivitet.periode.sortedFOMDate().first()
        } else {
            typeSMinfo?.periode?.arbufoerFOM ?: throw RuntimeException("Unable to find første fraværsdag in IT")
        }
    }

    fun produceManualTaskAndSendValidationResults(
        kafkaProducer: KafkaProducer<String, ProduceTask>,
        receivedSykmelding: ReceivedSykmelding,
        validationResult: ValidationResult,
        navKontorNr: String,
        loggingMeta: LoggingMeta,
        oppgaveTopic: String,
        sm2013BehandlingsUtfallToipic: String,
        kafkaproducervalidationResult: KafkaProducer<String, ValidationResult>,
        itfh: InfotrygdForespAndHealthInformation,
        helsepersonellKategoriVerdi: String,
        jedis: Jedis,
        applicationState: ApplicationState
    ) {
        sendRuleCheckValidationResult(receivedSykmelding, kafkaproducervalidationResult,
                validationResult, sm2013BehandlingsUtfallToipic, loggingMeta)
        try {
            val perioder = itfh.healthInformation.aktivitet.periode.sortedBy { it.periodeFOMDato }
            val forsteFravaersDag = itfh.healthInformation.aktivitet.periode.sortedFOMDate().first()
            val tssid = if (!receivedSykmelding.tssid.isNullOrBlank()) {
                receivedSykmelding.tssid
            } else {
                "0"
            }
            val sha256String = sha256hashstring(createInfotrygdBlokk(
                    itfh, perioder.first(), receivedSykmelding.personNrPasient, LocalDate.of(2019, 1, 1),
                    helsepersonellKategoriVerdi, tssid, loggingMeta, navKontorNr,
                    findarbeidsKategori(itfh.healthInformation.arbeidsgiver?.navnArbeidsgiver),
                    forsteFravaersDag, 1)
            )

            val duplikatInfotrygdOppdatering = erIRedis(sha256String, jedis)

            if (errorFromInfotrygd(validationResult.ruleHits)) {
                oppdaterAntallErrorIInfotrygd(INFOTRYGD, "1", jedis, TimeUnit.MINUTES.toSeconds(1).toInt(), loggingMeta)
            }

            val antallErrorFraInfotrygd = antallErrorIInfotrygd(INFOTRYGD, jedis, loggingMeta)

            if (antallErrorFraInfotrygd > 50) {
                log.error("Setter applicationState.ready til false")
                applicationState.ready = false
            }

            val skalIkkeOppdatereInfotrygd = skalIkkeOppdatereInfotrygd(receivedSykmelding, validationResult)

            if (duplikatInfotrygdOppdatering) {
                log.warn("Melding market som infotrygd duplikat, ikkje opprett manuelloppgave {}", StructuredArguments.fields(loggingMeta))
            } else if (skalIkkeOppdatereInfotrygd) {
                log.warn("Melding market som unødvendig å oppdatere infotrygd, ikkje opprett manuelloppgave {}", StructuredArguments.fields(loggingMeta))
            } else {
                createTask(kafkaProducer, receivedSykmelding, validationResult, navKontorNr, loggingMeta, oppgaveTopic)
                oppdaterRedis(sha256String, sha256String, jedis, TimeUnit.DAYS.toSeconds(60).toInt(), loggingMeta)
            }
        } catch (connectionException: JedisConnectionException) {
            log.error("Fikk ikkje opprettet kontakt med redis, kaster exception", connectionException)
            throw connectionException
        }
    }

    fun createTask(
        kafkaProducer: KafkaProducer<String,
            ProduceTask>,
        receivedSykmelding: ReceivedSykmelding,
        validationResult: ValidationResult,
        navKontorNr: String,
        loggingMeta: LoggingMeta,
        oppgaveTopic: String
    ) {
        kafkaProducer.send(ProducerRecord(oppgaveTopic, receivedSykmelding.sykmelding.id,
                ProduceTask().apply {
                    messageId = receivedSykmelding.msgId
                    aktoerId = receivedSykmelding.sykmelding.pasientAktoerId
                    tildeltEnhetsnr = navKontorNr
                    opprettetAvEnhetsnr = "9999"
                    behandlesAvApplikasjon = "FS22" // Gosys
                    orgnr = receivedSykmelding.legekontorOrgNr ?: ""
                    beskrivelse = "Manuell behandling av sykmelding grunnet følgende regler: ${validationResult.ruleHits.joinToString(", ", "(", ")") { it.messageForSender }}"
                    temagruppe = "ANY"
                    tema = "SYM"
                    behandlingstema = "ANY"
                    oppgavetype = "BEH_EL_SYM"
                    behandlingstype = "ANY"
                    mappeId = 1
                    aktivDato = DateTimeFormatter.ISO_DATE.format(LocalDate.now())
                    fristFerdigstillelse = DateTimeFormatter.ISO_DATE.format(LocalDate.now())
                    prioritet = PrioritetType.NORM
                    metadata = mapOf()
                }))
        MANUELLE_OPPGAVER_COUNTER.inc()
        log.info("Message sendt to topic: {}, {}", oppgaveTopic, StructuredArguments.fields(loggingMeta))
    }

    fun errorFromInfotrygd(rules: List<RuleInfo>): Boolean =
        rules.any { ruleInfo ->
            ruleInfo.ruleName.equals("ERROR_FROM_IT_HOUVED_STATUS_KODEMELDING") ||
            ruleInfo.ruleName.equals("ERROR_FROM_IT_SMHISTORIKK_STATUS_KODEMELDING") ||
            ruleInfo.ruleName.equals("ERROR_FROM_IT_PARALELLYTELSER_STATUS_KODEMELDING") ||
            ruleInfo.ruleName.equals("ERROR_FROM_IT_PASIENT_UTREKK_STATUS_KODEMELDING") ||
            ruleInfo.ruleName.equals("ERROR_FROM_IT_DIAGNOSE_OK_UTREKK_STATUS_KODEMELDING")
        }

    fun skalIkkeOppdatereInfotrygd(
        receivedSykmelding: ReceivedSykmelding,
        validationResult: ValidationResult
    ): Boolean =
            validationResult.ruleHits.isNotEmpty() && validationResult.ruleHits.any {
                it.ruleName == ValidationRuleChain.PARTIALLY_COINCIDENT_SICK_LEAVE_PERIOD_WITH_PREVIOUSLY_REGISTERED_SICK_LEAVE.name
            } && receivedSykmelding.sykmelding.perioder.sortedPeriodeFOMDate().lastOrNull() != null && receivedSykmelding.sykmelding.perioder.sortedPeriodeTOMDate().lastOrNull() != null && (receivedSykmelding.sykmelding.perioder.sortedPeriodeFOMDate().last()..receivedSykmelding.sykmelding.perioder.sortedPeriodeTOMDate().last()).daysBetween() <= 3
}
