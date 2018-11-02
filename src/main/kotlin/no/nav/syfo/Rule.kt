package no.nav.syfo

import no.nav.syfo.model.Status

data class Rule<in T>(val name: String, val outcomeType: OutcomeType, val description: String, val predicate: (T) -> Boolean)
data class Outcome(val outcomeType: OutcomeType, val description: String)

data class RuleChain<in T>(val name: String, val description: String, val rules: List<Rule<T>>) {
    fun executeFlow(input: T): List<Outcome> = rules
            .filter { RULE_HIT_SUMMARY.labels(it.outcomeType.name).startTimer().use { _ -> it.predicate(input) } }
            .onEach { RULE_HIT_COUNTER.labels(it.outcomeType.name) }
            .map { Outcome(it.outcomeType, it.description) }
}

enum class OutcomeType(val ruleId: Int, val status: Status) {
    PATIENT_NOT_IN_IP(1501, Status.MANUAL_PROCESSING),
    MESSAGE_NOT_IN_INFOTRYGD(1510, Status.MANUAL_PROCESSING),
    PARTIALLY_COINCIDENT_SICK_LEAVE_PERIOD_WITH_PREVIOUSLY_REGISTERED_SICK_LEAVE(1513, Status.MANUAL_PROCESSING),
    SICK_LEAVE_PERIOD_OVER_1_YEAR(1514, Status.MANUAL_PROCESSING),
    SICKLEAVE_EXTENTION_FROM_DIFFRENT_NAV_OFFICE(1515, Status.MANUAL_PROCESSING),
    NEW_CLEAN_BILL_DATE_BEFORE_ARBUFORTOM(1516, Status.MANUAL_PROCESSING),
    NEW_CLEAN_BILL_DATE_BEFORE_PAYOUT(1517, Status.MANUAL_PROCESSING),
    NEW_CLEAN_BILL_DATE_BEFORE_REGISTERD_CLEAN_BILL_DATE(1518, Status.MANUAL_PROCESSING),
    DOCTOR_IS_MT_AND_OVER_12_WEEKS(1519, Status.INVALID), // TODO this rule should not be here it should be i syfosykemeldingregler
    DOCTOR_IS_KI_AND_OVER_12_WEEKS(1520, Status.INVALID), // TODO this rule should not be here it should be i syfosykemeldingregler
    DIABILITY_GRADE_CANGED(1530, Status.MANUAL_PROCESSING), // TODO maybe auto update IT
    EXTANION_OVER_FA(1544, Status.MANUAL_PROCESSING),
    PATIENT_DEAD(1545, Status.MANUAL_PROCESSING),
    PERSON_MOVING_KODE_FL(1546, Status.MANUAL_PROCESSING),
    PERIOD_ENDED_DEAD(1548, Status.MANUAL_PROCESSING),
    PERIOD_FOR_AA_ENDED(1549, Status.MANUAL_PROCESSING),
    PERIOD_IS_AF(1550, Status.MANUAL_PROCESSING), // TODO maybe change what we do with this
    MAX_SICK_LEAVE_PAYOUT(1551, Status.MANUAL_PROCESSING),
    REFUSAL_IS_REGISTERED(1552, Status.MANUAL_PROCESSING),
    ERROR_FROM_IT(1591, Status.MANUAL_PROCESSING)
}
