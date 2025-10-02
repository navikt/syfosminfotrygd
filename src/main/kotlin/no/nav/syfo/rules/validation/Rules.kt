package no.nav.syfo.rules.validation

import java.time.LocalDate
import no.nav.helse.infotrygd.foresp.TypeSMinfo
import no.nav.syfo.model.RuleMetadata
import no.nav.syfo.model.sykmelding.Periode
import no.nav.syfo.model.sykmelding.Sykmelding
import no.nav.syfo.rules.dsl.RuleResult

typealias Rule<T> = (sykmelding: Sykmelding, ruleMetadata: RuleMetadata) -> RuleResult<T>

typealias ValidationRule = Rule<ValidationRules>

val numberOfTrementsDaySet: ValidationRule = { sykmelding, _ ->
    val perioder = sykmelding.perioder

    RuleResult(
        ruleInputs = mapOf("perioder" to perioder),
        rule = ValidationRules.NUMBER_OF_TREATMENT_DAYS_SET,
        ruleResult = perioder.any { it.behandlingsdager != null },
    )
}

val gradertReiseTilskuddErOppgitt: ValidationRule = { sykmelding, _ ->
    val perioder = sykmelding.perioder

    RuleResult(
        ruleInputs = mapOf("perioder" to perioder),
        rule = ValidationRules.GRADERT_REISETILSKUDD_ER_OPPGITT,
        ruleResult = perioder.any { it.gradert?.reisetilskudd ?: false },
    )
}

val travelSubSidySpecified: ValidationRule = { sykmelding, _ ->
    val perioder = sykmelding.perioder

    RuleResult(
        ruleInputs = mapOf("perioder" to perioder),
        rule = ValidationRules.TRAVEL_SUBSIDY_SPECIFIED,
        ruleResult = perioder.any { it.reisetilskudd },
    )
}

val patientNotInIP: ValidationRule = { _, ruleMetadata ->
    val pasient = ruleMetadata.infotrygdForesp.pasient

    RuleResult(
        ruleInputs = mapOf("pasient" to pasient),
        rule = ValidationRules.PATIENT_NOT_IN_IP,
        ruleResult = pasient?.isFinnes != null && !pasient.isFinnes,
    )
}

val partiallConincidentSickLeavePeriodWithPreviousRegistertSickLave: ValidationRule =
    { sykmelding, ruleMetadata ->
        val infotrygdSykmelding = ruleMetadata.infotrygdForesp.sMhistorikk?.sykmelding
        val sykmeldingPerioder = sykmelding.perioder

        RuleResult(
            ruleInputs =
                mapOf(
                    "infotrygdSykmelding" to (infotrygdSykmelding ?: ""),
                    "sykmeldingPerioder" to sykmeldingPerioder,
                ),
            rule =
                ValidationRules
                    .PARTIALLY_COINCIDENT_SICK_LEAVE_PERIOD_WITH_PREVIOUSLY_REGISTERED_SICK_LEAVE,
            ruleResult =
                infotrygdSykmelding != null &&
                    infotrygdSykmelding.sortedSMInfos().lastOrNull()?.periode?.arbufoerFOM !=
                        null &&
                    infotrygdSykmelding.sortedTOMDate().lastOrNull() != null &&
                    infotrygdSykmelding.sortedFOMDate().firstOrNull() != null &&
                    sykmeldingPerioder.sortedPeriodeFOMDate().firstOrNull() != null &&
                    sykmeldingPerioder.sortedPeriodeTOMDate().lastOrNull() != null &&
                    (sykmeldingPerioder
                        .sortedPeriodeFOMDate()
                        .first()
                        .isBefore(infotrygdSykmelding.sortedFOMDate().first()) ||
                        sykmeldingPerioder
                            .sortedPeriodeTOMDate()
                            .last()
                            .isBefore(infotrygdSykmelding.sortedTOMDate().last())),
        )
    }

val sickLaveExtenionFromDiffrentNavOffice1: ValidationRule = { sykmelding, ruleMetadata ->
    val infotrygdSykmelding = ruleMetadata.infotrygdForesp.sMhistorikk?.sykmelding
    val sykmeldingPerioder = sykmelding.perioder

    RuleResult(
        ruleInputs =
            mapOf(
                "infotrygdSykmelding" to (infotrygdSykmelding ?: ""),
                "sykmeldingPerioder" to sykmeldingPerioder,
            ),
        rule = ValidationRules.SICKLEAVE_EXTENTION_FROM_DIFFRENT_NAV_OFFICE_1,
        ruleResult =
            infotrygdSykmelding?.sortedSMInfos()?.lastOrNull()?.periode?.arbufoerFOM != null &&
                sykmeldingPerioder.sortedPeriodeFOMDate().lastOrNull() != null &&
                !infotrygdSykmelding.sortedSMInfos().last().periode?.friskKode.isNullOrBlank() &&
                !infotrygdSykmelding
                    .sortedSMInfos()
                    .last()
                    .periode
                    ?.hovedDiagnosekode
                    .isNullOrBlank() &&
                infotrygdSykmelding
                    .sortedSMInfos()
                    .last()
                    .periode
                    .arbufoerFOM
                    .isBefore(
                        sykmeldingPerioder.sortedPeriodeFOMDate().last(),
                    ) &&
                infotrygdSykmelding.sortedSMInfos().last().periode?.utbetTOM != null &&
                infotrygdSykmelding
                    .sortedSMInfos()
                    .last()
                    .periode
                    .utbetTOM
                    .isAfter(
                        sykmeldingPerioder.sortedPeriodeFOMDate().last(),
                    ) &&
                infotrygdSykmelding.sortedSMInfos().last().periode.hovedDiagnosekode != "000" &&
                infotrygdSykmelding.sortedSMInfos().last().periode.friskKode != "H",
    )
}
val sickLaveExtenionFromDiffrentNavOffice2: ValidationRule = { sykmelding, ruleMetadata ->
    val infotrygdSykmelding = ruleMetadata.infotrygdForesp.sMhistorikk?.sykmelding
    val sykmeldingPerioder = sykmelding.perioder

    RuleResult(
        ruleInputs =
            mapOf(
                "infotrygdSykmelding" to (infotrygdSykmelding ?: ""),
                "sykmeldingPerioder" to sykmeldingPerioder,
            ),
        rule = ValidationRules.SICKLEAVE_EXTENTION_FROM_DIFFRENT_NAV_OFFICE_2,
        ruleResult =
            infotrygdSykmelding?.sortedSMInfos()?.lastOrNull() != null &&
                !infotrygdSykmelding.sortedSMInfos().last().periode?.friskKode.isNullOrBlank() &&
                !infotrygdSykmelding
                    .sortedSMInfos()
                    .last()
                    .periode
                    ?.hovedDiagnosekode
                    .isNullOrBlank() &&
                infotrygdSykmelding.sortedSMInfos().last().periode?.utbetTOM != null &&
                infotrygdSykmelding
                    .sortedSMInfos()
                    .last()
                    .periode
                    .utbetTOM
                    .plusDays(1)
                    .equals(sykmeldingPerioder.sortedPeriodeFOMDate().last()) &&
                infotrygdSykmelding.sortedSMInfos().last().periode.hovedDiagnosekode != "000" &&
                infotrygdSykmelding.sortedSMInfos().last().periode.friskKode != "H",
    )
}

val sickLaveExtenionFromDiffrentNavOffice3: ValidationRule = { sykmelding, ruleMetadata ->
    val infotrygdSykmelding = ruleMetadata.infotrygdForesp.sMhistorikk?.sykmelding
    val sykmeldingPerioder = sykmelding.perioder

    RuleResult(
        ruleInputs =
            mapOf(
                "infotrygdSykmelding" to (infotrygdSykmelding ?: ""),
                "sykmeldingPerioder" to sykmeldingPerioder,
            ),
        rule = ValidationRules.SICKLEAVE_EXTENTION_FROM_DIFFRENT_NAV_OFFICE_3,
        ruleResult =
            sykmeldingPerioder.sortedPeriodeFOMDate().lastOrNull() != null &&
                infotrygdSykmelding?.sortedSMInfos()?.lastOrNull()?.periode?.utbetTOM != null &&
                !infotrygdSykmelding
                    .sortedSMInfos()
                    .lastOrNull()
                    ?.periode
                    ?.friskKode
                    .isNullOrBlank() &&
                !infotrygdSykmelding
                    .sortedSMInfos()
                    .lastOrNull()
                    ?.periode
                    ?.hovedDiagnosekode
                    .isNullOrBlank() &&
                infotrygdSykmelding
                    .sortedSMInfos()
                    .last()
                    .periode
                    .utbetTOM
                    .isBefore(
                        sykmeldingPerioder.sortedPeriodeFOMDate().last(),
                    ) &&
                infotrygdSykmelding
                    .sortedSMInfos()
                    .last()
                    .periode
                    .utbetTOM
                    .plusDays(3)
                    .isAfter(
                        sykmeldingPerioder.sortedPeriodeFOMDate().last(),
                    ) &&
                infotrygdSykmelding.sortedSMInfos().last().periode.utbetTOM.dayOfWeek >=
                    java.time.DayOfWeek.FRIDAY &&
                sykmeldingPerioder.sortedPeriodeFOMDate().last().dayOfWeek in
                    arrayOf(
                        java.time.DayOfWeek.SATURDAY,
                        java.time.DayOfWeek.SUNDAY,
                        java.time.DayOfWeek.MONDAY,
                    ) &&
                infotrygdSykmelding.sortedSMInfos().last().periode.hovedDiagnosekode != "000" &&
                infotrygdSykmelding.sortedSMInfos().last().periode.friskKode != "H",
    )
}

val newCleanBillDateBeforePayout: ValidationRule = { sykmelding, ruleMetadata ->
    val infotrygdSykmelding = ruleMetadata.infotrygdForesp.sMhistorikk?.sykmelding
    val sykmeldingPerioder = sykmelding.perioder
    val sykmeldingPrognose = sykmelding.prognose

    RuleResult(
        ruleInputs =
            mapOf(
                "infotrygdSykmelding" to (infotrygdSykmelding ?: ""),
                "sykmeldingPerioder" to sykmeldingPerioder,
                "sykmeldingPrognose" to (sykmeldingPrognose ?: ""),
            ),
        rule = ValidationRules.NEW_CLEAN_BILL_DATE_BEFORE_PAYOUT,
        ruleResult =
            sykmeldingPrognose?.arbeidsforEtterPeriode != null &&
                sykmeldingPrognose.arbeidsforEtterPeriode &&
                infotrygdSykmelding != null &&
                sykmeldingPerioder.sortedPeriodeTOMDate().lastOrNull() != null &&
                infotrygdSykmelding.sortedSMInfos().lastOrNull()?.periode?.utbetTOM != null &&
                sykmeldingPerioder
                    .sortedPeriodeTOMDate()
                    .last()
                    .isBefore(
                        infotrygdSykmelding.sortedSMInfos().last().periode.utbetTOM,
                    ),
    )
}

val newCleanBillDateBeforeRegisteredCleanBillDate: ValidationRule = { sykmelding, ruleMetadata ->
    val infotrygdSykmelding = ruleMetadata.infotrygdForesp.sMhistorikk?.sykmelding
    val sykmeldingPerioder = sykmelding.perioder
    val sykmeldingPrognose = sykmelding.prognose

    RuleResult(
        ruleInputs =
            mapOf(
                "infotrygdSykmelding" to (infotrygdSykmelding ?: ""),
                "sykmeldingPerioder" to sykmeldingPerioder,
                "sykmeldingPrognose" to (sykmeldingPrognose ?: ""),
            ),
        rule = ValidationRules.NEW_CLEAN_BILL_DATE_BEFORE_REGISTERD_CLEAN_BILL_DATE,
        ruleResult =
            infotrygdSykmelding?.sortedSMInfos()?.lastOrNull()?.periode?.friskmeldtDato != null &&
                sykmeldingPrognose?.arbeidsforEtterPeriode != null &&
                sykmeldingPrognose.arbeidsforEtterPeriode &&
                sykmeldingPerioder.sortedPeriodeTOMDate().lastOrNull() != null &&
                infotrygdSykmelding.sortedSMInfos().lastOrNull()?.periode?.friskmeldtDato != null &&
                sykmeldingPerioder
                    .sortedPeriodeTOMDate()
                    .last()
                    .plusDays(1)
                    .isBefore(
                        infotrygdSykmelding.sortedSMInfos().last().periode.friskmeldtDato,
                    ),
    )
}

val extaionOverFa: ValidationRule = { sykmelding, ruleMetadata ->
    val infotrygdSykmelding = ruleMetadata.infotrygdForesp.sMhistorikk?.sykmelding
    val sykmeldingPerioder = sykmelding.perioder

    RuleResult(
        ruleInputs =
            mapOf(
                "infotrygdSykmelding" to (infotrygdSykmelding ?: ""),
                "sykmeldingPerioder" to sykmeldingPerioder,
            ),
        rule = ValidationRules.EXTANION_OVER_FA,
        ruleResult =
            infotrygdSykmelding != null &&
                sykmeldingPerioder.sortedPeriodeFOMDate().lastOrNull() != null &&
                infotrygdSykmelding
                    .sortedSMInfos()
                    .lastOrNull()
                    ?.historikk
                    ?.sortedSMinfoHistorikk()
                    ?.lastOrNull()
                    ?.tilltak
                    ?.type != null &&
                infotrygdSykmelding
                    .sortedSMInfos()
                    .last()
                    .historikk
                    .sortedSMinfoHistorikk()
                    .last()
                    .tilltak
                    .type == "FA" &&
                infotrygdSykmelding
                    .sortedSMInfos()
                    .last()
                    .historikk
                    .sortedSMinfoHistorikk()
                    .last()
                    .tilltak
                    .tom != null &&
                sykmeldingPerioder.any { periodA ->
                    infotrygdSykmelding.any { sykmeldinger ->
                        sykmeldinger.historikk.any { historikk ->
                            historikk?.tilltak != null &&
                                historikk.tilltak.type == "FA" &&
                                historikk.tilltak.fom in periodA.range() ||
                                historikk?.tilltak != null &&
                                    historikk.tilltak.tom in periodA.range()
                        }
                    }
                },
    )
}

val personMovingKodeFl: ValidationRule = { _, ruleMetadata ->
    val infotrygdSykmelding = ruleMetadata.infotrygdForesp.sMhistorikk?.sykmelding

    RuleResult(
        ruleInputs =
            mapOf(
                "infotrygdSykmelding" to (infotrygdSykmelding ?: ""),
            ),
        rule = ValidationRules.PERSON_MOVING_KODE_FL,
        ruleResult =
            infotrygdSykmelding
                ?.find {
                    it.periode?.arbufoerFOM != null &&
                        it.periode.arbufoerFOM.equals(
                            infotrygdSykmelding.sortedFOMDate().lastOrNull()
                        )
                }
                ?.periode
                ?.stans == "FL",
    )
}

val periodForAAEnded: ValidationRule = { sykmelding, ruleMetadata ->
    val infotrygdSykmelding = ruleMetadata.infotrygdForesp.sMhistorikk?.sykmelding
    val sykmeldingPerioder = sykmelding.perioder
    val sykmeldingPrognose = sykmelding.prognose

    RuleResult(
        ruleInputs =
            mapOf(
                "infotrygdSykmelding" to (infotrygdSykmelding ?: ""),
                "sykmeldingPerioder" to sykmeldingPerioder,
                "sykmeldingPrognose" to (sykmeldingPrognose ?: ""),
            ),
        rule = ValidationRules.PERIOD_FOR_AA_ENDED,
        ruleResult =
            sykmeldingPerioder.any {
                !infotrygdSykmelding
                    ?.sortedSMInfos()
                    ?.lastOrNull()
                    ?.periode
                    ?.stans
                    .isNullOrBlank() &&
                    ruleMetadata.infotrygdForesp.sMhistorikk.sykmelding
                        .sortedSMInfos()
                        .last()
                        .periode
                        .arbufoerTOM != null &&
                    ruleMetadata.infotrygdForesp.sMhistorikk.sykmelding
                        .sortedSMInfos()
                        .last()
                        .periode
                        .stans == "AA" &&
                    it.fom.isBefore(
                        ruleMetadata.infotrygdForesp.sMhistorikk.sykmelding
                            .sortedSMInfos()
                            .last()
                            .periode
                            .arbufoerTOM,
                    )
            },
    )
}

val maxSickLeavePayout: ValidationRule = { sykmelding, ruleMetadata ->
    val sykmeldingPerioder = sykmelding.perioder
    val infotrygdSykmelding = ruleMetadata.infotrygdForesp.sMhistorikk?.sykmelding

    RuleResult(
        ruleInputs =
            mapOf(
                "infotrygdSykmelding" to (infotrygdSykmelding ?: ""),
                "sykmeldingPerioder" to sykmeldingPerioder,
            ),
        rule = ValidationRules.MAX_SICK_LEAVE_PAYOUT,
        ruleResult =
            sykmeldingPerioder.any {
                !infotrygdSykmelding
                    ?.sortedSMInfos()
                    ?.lastOrNull()
                    ?.periode
                    ?.stans
                    .isNullOrBlank() &&
                    infotrygdSykmelding?.sortedSMInfos()?.last()?.periode?.stans == "MAX" &&
                    infotrygdSykmelding.sortedSMInfos().last().periode.arbufoerTOM != null &&
                    it.fom.isBefore(
                        infotrygdSykmelding
                            .sortedSMInfos()
                            .last()
                            .periode
                            .arbufoerTOM
                            .plusMonths(6),
                    )
            },
    )
}

val periodIsAf: ValidationRule = { sykmelding, ruleMetadata ->
    RuleResult(
        ruleInputs =
            mapOf(
                "sykmelding" to sykmelding,
            ),
        rule = ValidationRules.PERIOD_IS_AF,
        ruleResult =
            sykmelding.perioder.any {
                !ruleMetadata.infotrygdForesp.sMhistorikk
                    ?.sykmelding
                    ?.sortedSMInfos()
                    ?.lastOrNull()
                    ?.periode
                    ?.stans
                    .isNullOrBlank() &&
                    ruleMetadata.infotrygdForesp.sMhistorikk.sykmelding
                        .sortedSMInfos()
                        .last()
                        .periode
                        .arbufoerTOM != null &&
                    ruleMetadata.infotrygdForesp.sMhistorikk.sykmelding
                        .sortedSMInfos()
                        .last()
                        .periode
                        .stans == "AF" &&
                    it.fom.isBefore(
                        ruleMetadata.infotrygdForesp.sMhistorikk.sykmelding
                            .sortedSMInfos()
                            .last()
                            .periode
                            .arbufoerTOM,
                    )
            },
    )
}

val errorFromItHouvedStatusKodeMelding: ValidationRule = { _, ruleMetadata ->
    val hovedStatusKodeMelding = ruleMetadata.infotrygdForesp.hovedStatus?.kodeMelding

    RuleResult(
        ruleInputs =
            mapOf(
                "hovedStatusKodeMelding" to (hovedStatusKodeMelding ?: ""),
            ),
        rule = ValidationRules.ERROR_FROM_IT_HOUVED_STATUS_KODEMELDING,
        ruleResult =
            hovedStatusKodeMelding?.toIntOrNull() != null && hovedStatusKodeMelding.toInt() > 4,
    )
}

val errorFromItSmgistorikkStatusKodemelding: ValidationRule = { _, ruleMetadata ->
    val smHistorikkKodeMelding = ruleMetadata.infotrygdForesp.sMhistorikk?.status?.kodeMelding

    RuleResult(
        ruleInputs =
            mapOf(
                "smHistorikkKodeMelding" to (smHistorikkKodeMelding ?: ""),
            ),
        rule = ValidationRules.ERROR_FROM_IT_SMHISTORIKK_STATUS_KODEMELDING,
        ruleResult =
            smHistorikkKodeMelding?.toIntOrNull() != null && smHistorikkKodeMelding.toInt() > 4,
    )
}
val errorFromItParalellytelserStatusKodemelding: ValidationRule = { _, ruleMetadata ->
    val parallelleYtelsesKodeMelding =
        ruleMetadata.infotrygdForesp.parallelleYtelser?.status?.kodeMelding

    RuleResult(
        ruleInputs =
            mapOf(
                "parallelleYtelsesKodeMelding" to (parallelleYtelsesKodeMelding ?: ""),
            ),
        rule = ValidationRules.ERROR_FROM_IT_PARALELLYTELSER_STATUS_KODEMELDING,
        ruleResult =
            parallelleYtelsesKodeMelding?.toIntOrNull() != null &&
                parallelleYtelsesKodeMelding.toInt() > 4,
    )
}
val errorFromItDiagnoseOkUtrekkStatusKodemelding: ValidationRule = { _, ruleMetadata ->
    val diagnoseKodeKodeMelding = ruleMetadata.infotrygdForesp.diagnosekodeOK?.status?.kodeMelding

    RuleResult(
        ruleInputs =
            mapOf(
                "diagnoseKodeKodeMelding" to (diagnoseKodeKodeMelding ?: ""),
            ),
        rule = ValidationRules.ERROR_FROM_IT_DIAGNOSE_OK_UTREKK_STATUS_KODEMELDING,
        ruleResult =
            diagnoseKodeKodeMelding?.toIntOrNull() != null && diagnoseKodeKodeMelding.toInt() > 4,
    )
}
val errorFromItPasientUtrekkStatusKodemelding: ValidationRule = { _, ruleMetadata ->
    val pasientStatusKodeMelding = ruleMetadata.infotrygdForesp.pasient?.status?.kodeMelding

    RuleResult(
        ruleInputs =
            mapOf(
                "pasientStatusKodeMelding" to (pasientStatusKodeMelding ?: ""),
            ),
        rule = ValidationRules.ERROR_FROM_IT_PASIENT_UTREKK_STATUS_KODEMELDING,
        ruleResult =
            pasientStatusKodeMelding?.toIntOrNull() != null && pasientStatusKodeMelding.toInt() > 4,
    )
}
val arbeiduforetomMangler: ValidationRule = { _, ruleMetadata ->
    val sykmeldingInfotrygd = ruleMetadata.infotrygdForesp.sMhistorikk?.sykmelding
    val status = ruleMetadata.infotrygdForesp.sMhistorikk?.status

    RuleResult(
        ruleInputs =
            mapOf(
                "sykmelding" to (sykmeldingInfotrygd ?: ""),
                "status" to (status ?: ""),
            ),
        rule = ValidationRules.ARBEIDUFORETOM_MANGLER,
        ruleResult =
            sykmeldingInfotrygd != null &&
                status?.kodeMelding != "04" &&
                sykmeldingInfotrygd.sortedSMInfos().lastOrNull()?.periode?.arbufoerTOM == null,
    )
}

fun List<Periode>.sortedPeriodeTOMDate(): List<LocalDate> = map { it.tom }.sorted()

fun List<Periode>.sortedPeriodeFOMDate(): List<LocalDate> = map { it.fom }.sorted()

fun List<TypeSMinfo>.sortedSMInfos(): List<TypeSMinfo> = sortedBy { it.periode.arbufoerTOM }

fun List<TypeSMinfo>.sortedFOMDate(): List<LocalDate> =
    mapNotNull { it.periode.arbufoerFOM }.sorted()

fun List<TypeSMinfo>.sortedTOMDate(): List<LocalDate> =
    mapNotNull { it.periode.arbufoerTOM }.sorted()

fun List<TypeSMinfo.Historikk>.sortedSMinfoHistorikk(): List<TypeSMinfo.Historikk> = sortedBy {
    it.endringsDato
}

fun Periode.range(): ClosedRange<LocalDate> = fom.rangeTo(tom)
