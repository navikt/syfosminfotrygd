package no.nav.syfo.rules.validation

import no.nav.syfo.model.sykmelding.Status
import no.nav.syfo.rules.common.RuleResult
import no.nav.syfo.rules.dsl.RuleNode
import no.nav.syfo.rules.dsl.tree

enum class ValidationRules {
    NUMBER_OF_TREATMENT_DAYS_SET,
    GRADERT_REISETILSKUDD_ER_OPPGITT,
    TRAVEL_SUBSIDY_SPECIFIED,
    PATIENT_NOT_IN_IP,
    PARTIALLY_COINCIDENT_SICK_LEAVE_PERIOD_WITH_PREVIOUSLY_REGISTERED_SICK_LEAVE,
    SICKLEAVE_EXTENTION_FROM_DIFFRENT_NAV_OFFICE_1,
    SICKLEAVE_EXTENTION_FROM_DIFFRENT_NAV_OFFICE_2,
    SICKLEAVE_EXTENTION_FROM_DIFFRENT_NAV_OFFICE_3,
    NEW_CLEAN_BILL_DATE_BEFORE_PAYOUT,
    NEW_CLEAN_BILL_DATE_BEFORE_REGISTERD_CLEAN_BILL_DATE,
    EXTANION_OVER_FA,
    PERSON_MOVING_KODE_FL,
    PERIOD_FOR_AA_ENDED,
    PERIOD_IS_AF,
    MAX_SICK_LEAVE_PAYOUT,
    ERROR_FROM_IT_HOUVED_STATUS_KODEMELDING,
    ERROR_FROM_IT_SMHISTORIKK_STATUS_KODEMELDING,
    ERROR_FROM_IT_PARALELLYTELSER_STATUS_KODEMELDING,
    ERROR_FROM_IT_DIAGNOSE_OK_UTREKK_STATUS_KODEMELDING,
    ERROR_FROM_IT_PASIENT_UTREKK_STATUS_KODEMELDING,
    ARBEIDUFORETOM_MANGLER,
    MISSING_OR_INCORRECT_HOVEDDIAGNOSE,
}

val validationRuleTree =
    tree<ValidationRules, RuleResult>(ValidationRules.NUMBER_OF_TREATMENT_DAYS_SET) {
        yes(Status.MANUAL_PROCESSING, ValidationRuleHit.NUMBER_OF_TREATMENT_DAYS_SET)
        no(ValidationRules.GRADERT_REISETILSKUDD_ER_OPPGITT) {
            yes(Status.MANUAL_PROCESSING, ValidationRuleHit.GRADERT_REISETILSKUDD_ER_OPPGITT)
            no(ValidationRules.TRAVEL_SUBSIDY_SPECIFIED) {
                yes(Status.MANUAL_PROCESSING, ValidationRuleHit.TRAVEL_SUBSIDY_SPECIFIED)
                no(ValidationRules.PATIENT_NOT_IN_IP) {
                    yes(Status.MANUAL_PROCESSING, ValidationRuleHit.PATIENT_NOT_IN_IP)
                    no(
                        ValidationRules
                            .PARTIALLY_COINCIDENT_SICK_LEAVE_PERIOD_WITH_PREVIOUSLY_REGISTERED_SICK_LEAVE
                    ) {
                        yes(
                            Status.MANUAL_PROCESSING,
                            ValidationRuleHit
                                .PARTIALLY_COINCIDENT_SICK_LEAVE_PERIOD_WITH_PREVIOUSLY_REGISTERED_SICK_LEAVE,
                        )
                        no(ValidationRules.SICKLEAVE_EXTENTION_FROM_DIFFRENT_NAV_OFFICE_1) {
                            yes(
                                Status.MANUAL_PROCESSING,
                                ValidationRuleHit.SICKLEAVE_EXTENTION_FROM_DIFFRENT_NAV_OFFICE_1
                            )
                            no(ValidationRules.SICKLEAVE_EXTENTION_FROM_DIFFRENT_NAV_OFFICE_2) {
                                yes(
                                    Status.MANUAL_PROCESSING,
                                    ValidationRuleHit
                                        .SICKLEAVE_EXTENTION_FROM_DIFFRENT_NAV_OFFICE_2,
                                )
                                no(ValidationRules.SICKLEAVE_EXTENTION_FROM_DIFFRENT_NAV_OFFICE_3) {
                                    yes(
                                        Status.MANUAL_PROCESSING,
                                        ValidationRuleHit
                                            .SICKLEAVE_EXTENTION_FROM_DIFFRENT_NAV_OFFICE_3,
                                    )
                                    no(ValidationRules.NEW_CLEAN_BILL_DATE_BEFORE_PAYOUT) {
                                        yes(
                                            Status.MANUAL_PROCESSING,
                                            ValidationRuleHit.NEW_CLEAN_BILL_DATE_BEFORE_PAYOUT
                                        )
                                        no(
                                            ValidationRules
                                                .NEW_CLEAN_BILL_DATE_BEFORE_REGISTERD_CLEAN_BILL_DATE
                                        ) {
                                            yes(
                                                Status.MANUAL_PROCESSING,
                                                ValidationRuleHit
                                                    .NEW_CLEAN_BILL_DATE_BEFORE_REGISTERD_CLEAN_BILL_DATE,
                                            )
                                            no(ValidationRules.EXTANION_OVER_FA) {
                                                yes(
                                                    Status.MANUAL_PROCESSING,
                                                    ValidationRuleHit.EXTANION_OVER_FA
                                                )
                                                no(ValidationRules.PERSON_MOVING_KODE_FL) {
                                                    yes(
                                                        Status.MANUAL_PROCESSING,
                                                        ValidationRuleHit.PERSON_MOVING_KODE_FL
                                                    )
                                                    no(ValidationRules.PERIOD_FOR_AA_ENDED) {
                                                        yes(
                                                            Status.MANUAL_PROCESSING,
                                                            ValidationRuleHit.PERIOD_FOR_AA_ENDED
                                                        )
                                                        no(ValidationRules.PERIOD_IS_AF) {
                                                            yes(
                                                                Status.MANUAL_PROCESSING,
                                                                ValidationRuleHit.PERIOD_IS_AF
                                                            )
                                                            no(
                                                                ValidationRules
                                                                    .MAX_SICK_LEAVE_PAYOUT
                                                            ) {
                                                                yes(
                                                                    Status.MANUAL_PROCESSING,
                                                                    ValidationRuleHit
                                                                        .MAX_SICK_LEAVE_PAYOUT,
                                                                )
                                                                no(
                                                                    ValidationRules
                                                                        .ERROR_FROM_IT_HOUVED_STATUS_KODEMELDING
                                                                ) {
                                                                    yes(
                                                                        Status.MANUAL_PROCESSING,
                                                                        ValidationRuleHit
                                                                            .ERROR_FROM_IT_HOUVED_STATUS_KODEMELDING,
                                                                    )
                                                                    no(
                                                                        ValidationRules
                                                                            .ERROR_FROM_IT_SMHISTORIKK_STATUS_KODEMELDING
                                                                    ) {
                                                                        yes(
                                                                            Status
                                                                                .MANUAL_PROCESSING,
                                                                            ValidationRuleHit
                                                                                .ERROR_FROM_IT_SMHISTORIKK_STATUS_KODEMELDING,
                                                                        )
                                                                        no(
                                                                            ValidationRules
                                                                                .ERROR_FROM_IT_PARALELLYTELSER_STATUS_KODEMELDING
                                                                        ) {
                                                                            yes(
                                                                                Status
                                                                                    .MANUAL_PROCESSING,
                                                                                ValidationRuleHit
                                                                                    .ERROR_FROM_IT_PARALELLYTELSER_STATUS_KODEMELDING,
                                                                            )
                                                                            no(
                                                                                ValidationRules
                                                                                    .ERROR_FROM_IT_DIAGNOSE_OK_UTREKK_STATUS_KODEMELDING
                                                                            ) {
                                                                                yes(
                                                                                    Status
                                                                                        .MANUAL_PROCESSING,
                                                                                    ValidationRuleHit
                                                                                        .ERROR_FROM_IT_DIAGNOSE_OK_UTREKK_STATUS_KODEMELDING,
                                                                                )
                                                                                no(
                                                                                    ValidationRules
                                                                                        .ERROR_FROM_IT_PASIENT_UTREKK_STATUS_KODEMELDING
                                                                                ) {
                                                                                    yes(
                                                                                        Status
                                                                                            .MANUAL_PROCESSING,
                                                                                        ValidationRuleHit
                                                                                            .ERROR_FROM_IT_PASIENT_UTREKK_STATUS_KODEMELDING,
                                                                                    )
                                                                                    no(
                                                                                        ValidationRules
                                                                                            .MISSING_OR_INCORRECT_HOVEDDIAGNOSE
                                                                                    ) {
                                                                                        yes(
                                                                                            Status
                                                                                                .MANUAL_PROCESSING,
                                                                                            ValidationRuleHit
                                                                                                .MISSING_OR_INCORRECT_HOVEDDIAGNOSE
                                                                                        )
                                                                                        no(
                                                                                            ValidationRules
                                                                                                .ARBEIDUFORETOM_MANGLER
                                                                                        ) {
                                                                                            yes(
                                                                                                Status
                                                                                                    .MANUAL_PROCESSING,
                                                                                                ValidationRuleHit
                                                                                                    .ARBEIDUFORETOM_MANGLER,
                                                                                            )
                                                                                            no(
                                                                                                Status
                                                                                                    .OK
                                                                                            )
                                                                                        }
                                                                                    }
                                                                                }
                                                                            }
                                                                        }
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

internal fun RuleNode<ValidationRules, RuleResult>.yes(
    status: Status,
    ruleHit: ValidationRuleHit? = null
) {
    yes(RuleResult(status, ruleHit?.ruleHit))
}

internal fun RuleNode<ValidationRules, RuleResult>.no(
    status: Status,
    ruleHit: ValidationRuleHit? = null
) {
    no(RuleResult(status, ruleHit?.ruleHit))
}

fun getRule(rules: ValidationRules): Rule<ValidationRules> {
    return when (rules) {
        ValidationRules.NUMBER_OF_TREATMENT_DAYS_SET -> numberOfTrementsDaySet
        ValidationRules.GRADERT_REISETILSKUDD_ER_OPPGITT -> gradertReiseTilskuddErOppgitt
        ValidationRules.TRAVEL_SUBSIDY_SPECIFIED -> travelSubSidySpecified
        ValidationRules.PATIENT_NOT_IN_IP -> patientNotInIP
        ValidationRules
            .PARTIALLY_COINCIDENT_SICK_LEAVE_PERIOD_WITH_PREVIOUSLY_REGISTERED_SICK_LEAVE ->
            partiallConincidentSickLeavePeriodWithPreviousRegistertSickLave
        ValidationRules.SICKLEAVE_EXTENTION_FROM_DIFFRENT_NAV_OFFICE_1 ->
            sickLaveExtenionFromDiffrentNavOffice1
        ValidationRules.SICKLEAVE_EXTENTION_FROM_DIFFRENT_NAV_OFFICE_2 ->
            sickLaveExtenionFromDiffrentNavOffice2
        ValidationRules.SICKLEAVE_EXTENTION_FROM_DIFFRENT_NAV_OFFICE_3 ->
            sickLaveExtenionFromDiffrentNavOffice3
        ValidationRules.NEW_CLEAN_BILL_DATE_BEFORE_PAYOUT -> newCleanBillDateBeforePayout
        ValidationRules.NEW_CLEAN_BILL_DATE_BEFORE_REGISTERD_CLEAN_BILL_DATE ->
            newCleanBillDateBeforeRegisteredCleanBillDate
        ValidationRules.EXTANION_OVER_FA -> extaionOverFa
        ValidationRules.PERSON_MOVING_KODE_FL -> personMovingKodeFl
        ValidationRules.PERIOD_FOR_AA_ENDED -> periodForAAEnded
        ValidationRules.PERIOD_IS_AF -> periodIsAf
        ValidationRules.MAX_SICK_LEAVE_PAYOUT -> maxSickLeavePayout
        ValidationRules.ERROR_FROM_IT_HOUVED_STATUS_KODEMELDING ->
            errorFromItHouvedStatusKodeMelding
        ValidationRules.ERROR_FROM_IT_SMHISTORIKK_STATUS_KODEMELDING ->
            errorFromItSmgistorikkStatusKodemelding
        ValidationRules.ERROR_FROM_IT_PARALELLYTELSER_STATUS_KODEMELDING ->
            errorFromItParalellytelserStatusKodemelding
        ValidationRules.ERROR_FROM_IT_DIAGNOSE_OK_UTREKK_STATUS_KODEMELDING ->
            errorFromItDiagnoseOkUtrekkStatusKodemelding
        ValidationRules.ERROR_FROM_IT_PASIENT_UTREKK_STATUS_KODEMELDING ->
            errorFromItPasientUtrekkStatusKodemelding
        ValidationRules.ARBEIDUFORETOM_MANGLER -> arbeiduforetomMangler
        ValidationRules.MISSING_OR_INCORRECT_HOVEDDIAGNOSE -> missingOrIncorrectHoveddiagnose
    }
}
