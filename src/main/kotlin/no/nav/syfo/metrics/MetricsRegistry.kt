package no.nav.syfo.metrics

import io.prometheus.client.Counter
import io.prometheus.client.Summary

const val NAMESPACE = "syfosminfotrygd"

val REQUEST_TIME: Summary = Summary.build()
    .namespace(NAMESPACE)
    .name("request_time_ms")
    .help("Request time in milliseconds.").register()

val MANUELLE_OPPGAVER_COUNTER: Counter = Counter.Builder()
    .namespace(NAMESPACE)
    .name("manuelle_oppgaver_counter")
    .help("Antall manuelle oppgaver som er opprettet")
    .register()

val ANNEN_FRAVERS_ARSKAK_CHANGE_TO_A99_COUNTER: Counter = Counter.Builder()
    .namespace(NAMESPACE)
    .name("annen_fravers_arsak_change_to_a99_counter")
    .help("Counts the amount houveddiagnose is null and AnnenFraversArsak is set")
    .register()

val RULE_NODE_RULE_HIT_COUNTER: Counter = Counter.Builder()
    .namespace(NAMESPACE)
    .name("rulenode_rule_hit_counter")
    .labelNames("status", "rule_hit")
    .help("Counts rulenode rules")
    .register()

val RULE_NODE_RULE_PATH_COUNTER: Counter = Counter.Builder()
    .namespace(NAMESPACE)
    .name("rulenode_rule_path_counter")
    .labelNames("path")
    .help("Counts rulenode rule paths")
    .register()

val OVERLAPPER_PERIODER_COUNTER: Counter = Counter.Builder()
    .namespace(NAMESPACE)
    .name("overlapper_perioder")
    .labelNames("source")
    .help("Counts number of times we can skip producing oppgave")
    .register()

val OVERLAPPENDE_PERIODER_IKKE_OPPRETT_OPPGAVE: Counter = Counter.Builder()
    .namespace(NAMESPACE)
    .name("overlappende_perioder_ikke_opprett_oppgave")
    .help("counts number of times we dont create oppgave for overlapping sykmeldinger")
    .register()
