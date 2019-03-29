package no.nav.syfo

import no.nav.helse.sm2013.HelseOpplysningerArbeidsuforhet
import no.nav.syfo.metrics.RULE_HIT_COUNTER
import no.nav.syfo.model.Status

data class RuleData<T>(val healthInformation: HelseOpplysningerArbeidsuforhet, val metadata: T)

interface Rule<in T> {
    val name: String
    val ruleId: Int?
    val status: Status
    val textToUser: String
    val textToTreater: String
    val predicate: (T) -> Boolean
    operator fun invoke(input: T) = predicate(input)
}

inline fun <reified T, reified R : Rule<RuleData<T>>> List<R>.executeFlow(healthInformation: HelseOpplysningerArbeidsuforhet, value: T): List<Rule<Any>> =
        filter { it.predicate(RuleData(healthInformation, value)) }
                .map { it as Rule<Any> }
                .onEach { RULE_HIT_COUNTER.labels(it.name).inc() }

inline fun <reified T, reified R : Rule<RuleData<T>>> Array<R>.executeFlow(healthInformation: HelseOpplysningerArbeidsuforhet, value: T): List<Rule<Any>> = toList().executeFlow(healthInformation, value)

@Retention(AnnotationRetention.RUNTIME)
annotation class Description(val description: String)