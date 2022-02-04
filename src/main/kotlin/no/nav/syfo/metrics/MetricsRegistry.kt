package no.nav.syfo.metrics

import io.prometheus.client.Counter
import io.prometheus.client.Summary

const val NAMESPACE = "syfosminfotrygd"

val RULE_HIT_STATUS_COUNTER: Counter = Counter.Builder()
    .namespace(NAMESPACE)
    .name("rule_hit_status_counter")
    .labelNames("rule_status")
    .help("Registers a counter for each rule status")
    .register()

val REQUEST_TIME: Summary = Summary.build()
    .namespace(NAMESPACE)
    .name("request_time_ms")
    .help("Request time in milliseconds.").register()

val MANUELLE_OPPGAVER_COUNTER: Counter = Counter.Builder()
    .namespace(NAMESPACE)
    .name("manuelle_oppgaver_counter")
    .help("Antall manuelle oppgaver som er opprettet")
    .register()
