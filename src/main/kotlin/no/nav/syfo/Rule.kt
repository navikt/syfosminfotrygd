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
    TREATMENT_DAYS(1260, Status.MANUAL_PROCESSING),
    TRAVEL_GRANTS(1270, Status.MANUAL_PROCESSING),
    PATIENT_MISSSING_NAV_OFFICE(1302, Status.MANUAL_PROCESSING),
    PATIENT_EMIGRATED(1304, Status.MANUAL_PROCESSING),
    PATIENT_NOT_IN_INFOTRYGD(1501, Status.MANUAL_PROCESSING),
    MESSAGE_NOT_IN_INFOTRYGD(1510, Status.MANUAL_PROCESSING),
    RULE_AUTO(1511, Status.OK),
    PARTIALLY_COINCIDENT_SICK_LEAVE_PERIOD_WITH_PREVIOUSLY_REGISTERED_SICK_LEAVE(1513, Status.MANUAL_PROCESSING),
    SICKLEAVE_EXTENTION_FROM_DIFFRENT_NAV_OFFICE(1515, Status.MANUAL_PROCESSING),
    NEW_CLEAN_BILL_DATE_BEFORE_PAYOUT(1517, Status.MANUAL_PROCESSING),
    NEW_CLEAN_BILL_DATE_BEFORE_CLEAN_BILL_DATE(1518, Status.MANUAL_PROCESSING),
    PERSON_MOVING(1515, Status.MANUAL_PROCESSING),
    PERSON_MOVING2(1546, Status.MANUAL_PROCESSING),
    EXTANION_OVER_AA(1544, Status.MANUAL_PROCESSING),
    TRAVEL_GRANTS2(1547, Status.MANUAL_PROCESSING),
    PERIOD_FOR_AA_ENDED(1549, Status.MANUAL_PROCESSING),
    REFUSAL_IS_REGISTERED(1552, Status.MANUAL_PROCESSING)
}
