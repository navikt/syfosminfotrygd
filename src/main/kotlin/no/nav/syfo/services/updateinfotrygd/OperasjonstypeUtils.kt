package no.nav.syfo.services.updateinfotrygd

import io.opentelemetry.instrumentation.annotations.WithSpan
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.math.absoluteValue
import net.logstash.logback.argument.StructuredArguments
import no.nav.helse.infotrygd.foresp.TypeSMinfo
import no.nav.helse.sm2013.HelseOpplysningerArbeidsuforhet
import no.nav.syfo.InfotrygdForespAndHealthInformation
import no.nav.syfo.daysBetween
import no.nav.syfo.log
import no.nav.syfo.rules.validation.sortedSMInfos
import no.nav.syfo.util.LoggingMeta

enum class Operasjonstype {
    NY,
    UENDRET,
    UENDRET_IKKE_OPPDATER,
    ENDRING,
    FORLENGELSE,
    UGYLDIG_OVERLAPP
}

fun findoperasjonstypeAndFom(
    fom: LocalDate,
    tom: LocalDate,
    listSMinfo: List<TypeSMinfo>
): Pair<Operasjonstype, LocalDate> {

    if (listSMinfo.isEmpty()) {
        return Operasjonstype.NY to fom
    }
    val sortedSmInfo =
        listSMinfo
            .filter { it.periode?.arbufoerFOM != null }
            .sortedBy { it.periode.arbufoerFOM }
            .filter {
                it.periode.arbufoerTOM != null && it.periode.arbufoerTOM.isAfter(fom.minusDays(20))
            }

    for (i in sortedSmInfo.indices) {
        val currentFom = sortedSmInfo[i].periode.arbufoerFOM
        var currentTom = sortedSmInfo[i].periode.arbufoerTOM

        if (currentTom == null) {
            log.warn(
                "Sykmelding i infotrygd mangler tom-dato, bruker tom-dato fra sykmelding istedenfor"
            )
            currentTom = tom
        }

        if (tom.isBefore(currentFom.minusDays(1))) {
            return Operasjonstype.NY to fom
        }

        if (tom.isBefore(currentFom)) {
            return Operasjonstype.UGYLDIG_OVERLAPP to fom
        }

        if (fom.isBefore(currentFom) && tom.isAfter(currentFom.minusDays(1))) {
            return Operasjonstype.UGYLDIG_OVERLAPP to fom
        }

        if (!fom.isBefore(currentFom) && !tom.isAfter(currentTom)) {
            if (tom.isEqual(currentTom)) {
                return Operasjonstype.UENDRET to currentFom
            }
            return Operasjonstype.UENDRET_IKKE_OPPDATER to currentFom
        }

        if (fom.isEqual(currentFom) && tom.isAfter(currentTom)) {
            if (i < sortedSmInfo.size - 1) {
                val nextFom = sortedSmInfo[i + 1].periode.arbufoerFOM
                if (tom.isBefore(nextFom.minusDays(1))) {
                    return Operasjonstype.ENDRING to currentFom
                }
                return Operasjonstype.UGYLDIG_OVERLAPP to fom
            }
            return Operasjonstype.ENDRING to currentFom
        }

        if (!fom.isAfter(currentTom.plusDays(1))) {
            if (i < sortedSmInfo.size - 1) {
                val nextFom = sortedSmInfo[i + 1].periode.arbufoerFOM
                if (tom.isBefore(nextFom.minusDays(1))) {
                    return Operasjonstype.FORLENGELSE to currentFom
                }
                return Operasjonstype.UGYLDIG_OVERLAPP to fom
            }
            return Operasjonstype.FORLENGELSE to currentFom
        }
    }

    return Operasjonstype.NY to fom
}

@WithSpan
fun findOperasjonstype(
    periode: HelseOpplysningerArbeidsuforhet.Aktivitet.Periode,
    itfh: InfotrygdForespAndHealthInformation,
    loggingMeta: LoggingMeta,
): Int {
    // FORSTEGANGS = 1, PAFOLGENDE = 2, ENDRING = 3
    val typeSMinfo =
        itfh.infotrygdForesp.sMhistorikk?.sykmelding?.sortedSMInfos()?.lastOrNull() ?: return 1

    return when {
        endringSykmelding(periode, itfh, typeSMinfo) -> {
            3
        }
        paafolgendeSykmelding(periode, itfh, typeSMinfo) -> {
            2
        }
        forstegangsSykmelding(periode, itfh, typeSMinfo) -> {
            1
        }
        else -> {
            log.error(
                "Could not determined operasjonstype {}",
                StructuredArguments.fields(loggingMeta)
            )
            throw RuntimeException("Could not determined operasjonstype")
        }
    }
}

private fun forstegangsSykmelding(
    periode: HelseOpplysningerArbeidsuforhet.Aktivitet.Periode,
    itfh: InfotrygdForespAndHealthInformation,
    typeSMinfo: TypeSMinfo,
): Boolean =
    itfh.infotrygdForesp.sMhistorikk.status.kodeMelding == "04" ||
        (typeSMinfo.periode.arbufoerTOM != null &&
            (typeSMinfo.periode.arbufoerTOM..periode.periodeFOMDato).daysBetween().absoluteValue >=
                1)

private fun paafolgendeSykmelding(
    periode: HelseOpplysningerArbeidsuforhet.Aktivitet.Periode,
    itfh: InfotrygdForespAndHealthInformation,
    typeSMinfo: TypeSMinfo,
): Boolean =
    itfh.infotrygdForesp.sMhistorikk.status.kodeMelding != "04" &&
        periode.periodeFOMDato.isEqual(typeSMinfo.periode.arbufoerTOM) ||
        ((periode.periodeFOMDato.isAfter(typeSMinfo.periode.arbufoerTOM) &&
            ((typeSMinfo.periode.arbufoerTOM..periode.periodeFOMDato).daysBetween() <= 1 ||
                oppholdSkyldesHelg(
                    arbufoerTOM = typeSMinfo.periode.arbufoerTOM,
                    periodeFOMDato = periode.periodeFOMDato
                ))))

private fun oppholdSkyldesHelg(arbufoerTOM: LocalDate, periodeFOMDato: LocalDate): Boolean {
    return ((arbufoerTOM..periodeFOMDato).daysBetween() <= 3 &&
        arbufoerTOM.dayOfWeek == DayOfWeek.FRIDAY &&
        periodeFOMDato.dayOfWeek == DayOfWeek.MONDAY) ||
        ((arbufoerTOM..periodeFOMDato).daysBetween() <= 2 &&
            (arbufoerTOM.dayOfWeek == DayOfWeek.FRIDAY ||
                arbufoerTOM.dayOfWeek == DayOfWeek.SATURDAY) &&
            (periodeFOMDato.dayOfWeek == DayOfWeek.SUNDAY ||
                periodeFOMDato.dayOfWeek == DayOfWeek.MONDAY))
}

private fun endringSykmelding(
    periode: HelseOpplysningerArbeidsuforhet.Aktivitet.Periode,
    itfh: InfotrygdForespAndHealthInformation,
    typeSMinfo: TypeSMinfo,
): Boolean =
    itfh.infotrygdForesp.sMhistorikk.status.kodeMelding != "04" &&
        (typeSMinfo.periode.arbufoerFOM.equals(periode.periodeFOMDato) ||
            (typeSMinfo.periode.arbufoerFOM.isBefore(periode.periodeFOMDato)) ||
            (typeSMinfo.periode.arbufoerFOM != null &&
                sammePeriodeInfotrygd(typeSMinfo.periode, periode))) &&
        !(typeSMinfo.periode.arbufoerTOM == null &&
            (typeSMinfo.periode.arbufoerFOM..periode.periodeFOMDato).daysBetween() > 1) &&
        !(periode.periodeFOMDato.isEqual(typeSMinfo.periode.arbufoerTOM)) &&
        !(periode.periodeFOMDato.isAfter(typeSMinfo.periode.arbufoerTOM))

private fun sammePeriodeInfotrygd(
    infotrygdPeriode: TypeSMinfo.Periode,
    sykemldingsPeriode: HelseOpplysningerArbeidsuforhet.Aktivitet.Periode
): Boolean {
    return infotrygdPeriode.arbufoerFOM.equals(sykemldingsPeriode.periodeFOMDato) &&
        infotrygdPeriode.arbufoerTOM.equals(sykemldingsPeriode.periodeTOMDato)
}
