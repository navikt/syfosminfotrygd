package no.nav.syfo

import io.prometheus.client.Counter
import io.prometheus.client.Summary

const val NAMESPACE = "syfosminfotrygd"

val RULE_HIT_COUNTER: Counter = Counter.Builder().namespace(NAMESPACE).name("rule_hit_counter")
        .labelNames("rule_name").help("Registers a counter for each rule in the rule set").register()
val RULE_HIT_STATUS_COUNTER: Counter = Counter.Builder().namespace(NAMESPACE).name("rule_hit_status_counter")
        .labelNames("rule_status").help("Registers a counter for each rule status").register()
val NETWORK_CALL_SUMMARY: Summary = Summary.Builder().namespace(NAMESPACE).name("network_call_summary")
        .labelNames("http_endpoint").help("Summary for networked call times").register()