package no.nav.syfo

import no.nav.syfo.model.Status

interface Rule<in T> {
    val name: String
    val ruleId: Int?
    val status: Status
    val predicate: (T) -> Boolean
}

@Retention(AnnotationRetention.RUNTIME)
annotation class Description(val description: String)
