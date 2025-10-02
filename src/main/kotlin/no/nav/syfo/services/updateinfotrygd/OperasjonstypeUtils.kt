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
