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

val RULE_HIT_COUNTER: Counter = Counter.Builder()
    .namespace(NAMESPACE)
    .name("rule_hit_counter")
    .labelNames("rule_name")
    .help("Counts the amount of times a rule is hit").register()

val ANNEN_FRAVERS_ARSKAK_CHANGE_TO_A99_COUNTER: Counter = Counter.Builder()
    .namespace(NAMESPACE)
    .name("annen_fravers_arsak_change_to_a99_counter")
    .help("Counts the amount houveddiagnose is null and AnnenFraversArsak is set")
    .register()
