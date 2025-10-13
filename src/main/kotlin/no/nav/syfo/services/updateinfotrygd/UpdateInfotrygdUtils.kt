package no.nav.syfo.services.updateinfotrygd

import io.opentelemetry.instrumentation.annotations.WithSpan
import java.time.LocalDate
import net.logstash.logback.argument.StructuredArguments
import no.nav.helse.eiFellesformat.XMLEIFellesformat
import no.nav.helse.infotrygd.foresp.InfotrygdForesp
import no.nav.helse.sm2013.HelseOpplysningerArbeidsuforhet
import no.nav.helse.sm2013.KontrollSystemBlokk
import no.nav.helse.sm2013.KontrollsystemBlokkType
import no.nav.syfo.InfotrygdForespAndHealthInformation
import no.nav.syfo.UTENLANDSK_SYKEHUS
import no.nav.syfo.log
import no.nav.syfo.rules.validation.sortedSMInfos
import no.nav.syfo.unmarshal
import no.nav.syfo.util.LoggingMeta

@WithSpan
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
    behandletAvManuell: Boolean,
    utenlandskSykmelding: Boolean,
    operasjonstypeAndFom: Pair<Operasjonstype, LocalDate>,
) =
    KontrollsystemBlokkType.InfotrygdBlokk().apply {
        fodselsnummer = personNrPasient
        tkNummer = navKontorNr

        operasjonstype =
            when (operasjonstypeAndFom.first) {
                Operasjonstype.NY -> 1
                Operasjonstype.UENDRET -> 2
                Operasjonstype.ENDRING -> 2
                Operasjonstype.FORLENGELSE -> 3
                Operasjonstype.UGYLDIG_OVERLAPP ->
                    throw IllegalArgumentException(
                        "Ugyldig operasjonstype for infotrygd ${operasjonstypeAndFom.first}"
                    )
            }.toBigInteger()

        val typeSMinfo = itfh.infotrygdForesp.sMhistorikk?.sykmelding?.sortedSMInfos()?.lastOrNull()

        if (utenlandskSykmelding) {
            legeEllerInstitusjonsNummer = UTENLANDSK_SYKEHUS.toBigInteger()
        } else if (
            (typeSMinfo != null && tssid?.toBigInteger() != typeSMinfo.periode.legeInstNr) ||
                operasjonstype == 1.toBigInteger()
        ) {
            legeEllerInstitusjonsNummer = tssid?.toBigInteger() ?: "0".toBigInteger()
            legeEllerInstitusjon =
                if (itfh.healthInformation.behandler != null) {
                    itfh.healthInformation.behandler.formatName()
                } else {
                    ""
                }
        }

        forsteFravaersDag = operasjonstypeAndFom.second

        mottakerKode =
            if (utenlandskSykmelding) {
                "IN"
            } else {
                helsepersonellKategoriVerdi
            }

        if (itfh.infotrygdForesp.diagnosekodeOK != null) {
            hovedDiagnose = itfh.infotrygdForesp.hovedDiagnosekode
            hovedDiagnoseGruppe = itfh.infotrygdForesp.hovedDiagnosekodeverk.toBigInteger()
            hovedDiagnoseTekst = itfh.infotrygdForesp.diagnosekodeOK.diagnoseTekst
        }

        if (operasjonstype == 1.toBigInteger()) {
            behandlingsDato =
                if (behandletAvManuell) {
                    log.info(
                        "Bruker første fom som behandlingsdato for manuelt behandlet sykmelding {}",
                        StructuredArguments.fields(loggingMeta),
                    )
                    periode.periodeFOMDato
                } else {
                    findBehandlingsDato(itfh, signaturDato)
                }

            arbeidsKategori = findArbeidsKategori(navnArbeidsgiver)
            gruppe = "96"
            saksbehandler = "Auto"

            if (
                itfh.infotrygdForesp.biDiagnosekodeverk != null &&
                    itfh.healthInformation.medisinskVurdering.biDiagnoser.diagnosekode
                        .firstOrNull()
                        ?.dn != null &&
                    itfh.infotrygdForesp.diagnosekodeOK != null
            ) {
                biDiagnose = itfh.infotrygdForesp.biDiagnoseKode
                biDiagnoseGruppe = itfh.infotrygdForesp.biDiagnosekodeverk.toBigInteger()
                biDiagnoseTekst = itfh.infotrygdForesp.diagnosekodeOK.bidiagnoseTekst
            }
        }

        if (
            itfh.healthInformation.medisinskVurdering?.isSvangerskap != null &&
                itfh.healthInformation.medisinskVurdering.isSvangerskap
        ) {
            isErSvangerskapsrelatert = true
        }

        if (
            itfh.healthInformation.prognose != null &&
                itfh.healthInformation.prognose.isArbeidsforEtterEndtPeriode != null &&
                itfh.healthInformation.prognose.isArbeidsforEtterEndtPeriode
        ) {
            friskmeldtDato = periode.periodeTOMDato.plusDays(1)
        }

        arbeidsufoerTOM = periode.periodeTOMDato
        ufoeregrad =
            when {
                periode.gradertSykmelding != null ->
                    periode.gradertSykmelding.sykmeldingsgrad.toBigInteger()
                periode.aktivitetIkkeMulig != null -> 100.toBigInteger()
                else -> 0.toBigInteger()
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
    behandletAvManuell: Boolean,
    utenlandskSykmelding: Boolean,
    operasjonstypeAndFom: Pair<Operasjonstype, LocalDate>,
) =
    unmarshal<XMLEIFellesformat>(marshalledFellesformat).apply {
        any.add(
            KontrollSystemBlokk().apply {
                infotrygdBlokk.add(
                    createInfotrygdBlokk(
                        itfh = itfh,
                        periode = periode,
                        personNrPasient = personNrPasient,
                        signaturDato = signaturDato,
                        helsepersonellKategoriVerdi = helsepersonellKategoriVerdi,
                        tssid = tssid,
                        loggingMeta = loggingMeta,
                        navKontorNr = navKontorNr,
                        navnArbeidsgiver = itfh.healthInformation.arbeidsgiver?.navnArbeidsgiver,
                        behandletAvManuell = behandletAvManuell,
                        utenlandskSykmelding = utenlandskSykmelding,
                        operasjonstypeAndFom = operasjonstypeAndFom,
                    ),
                )
            },
        )
    }

fun findArbeidsKategori(navnArbeidsgiver: String?): String {
    return if (navnArbeidsgiver.isNullOrBlank() || navnArbeidsgiver.isEmpty()) {
        "30"
    } else {
        "01"
    }
}

private fun findBehandlingsDato(
    itfh: InfotrygdForespAndHealthInformation,
    signaturDato: LocalDate
): LocalDate {
    return if (
        itfh.healthInformation.kontaktMedPasient?.kontaktDato != null &&
            itfh.healthInformation.kontaktMedPasient?.behandletDato != null
    ) {
        listOf(
                itfh.healthInformation.kontaktMedPasient.kontaktDato,
                itfh.healthInformation.kontaktMedPasient.behandletDato.toLocalDate(),
            )
            .minOf { it }
    } else if (itfh.healthInformation.kontaktMedPasient?.behandletDato != null) {
        itfh.healthInformation.kontaktMedPasient.behandletDato.toLocalDate()
    } else {
        signaturDato
    }
}

private fun HelseOpplysningerArbeidsuforhet.Behandler.formatName(): String =
    if (navn.mellomnavn == null) {
        "${navn.etternavn.uppercase()} ${navn.fornavn.uppercase()}"
    } else {
        "${navn.etternavn.uppercase()} ${navn.fornavn.uppercase()} ${navn.mellomnavn.uppercase()}"
    }

fun InfotrygdForesp.getInfotrygdPerioder() = sMhistorikk?.sykmelding ?: emptyList()
