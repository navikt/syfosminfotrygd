package no.nav.syfo.metrics

import io.prometheus.client.Counter
import io.prometheus.client.Gauge
import io.prometheus.client.Summary

const val NAMESPACE = "syfosminfotrygd"

val RULE_HIT_STATUS_COUNTER: Counter = Counter.Builder()
        .namespace(NAMESPACE)
        .name("rule_hit_status_counter")
        .labelNames("rule_status")
        .help("Registers a counter for each rule status")
        .register()

val REQUEST_TIME: Summary = Summary.build()
        .namespace(METRICS_NS)
        .name("request_time_ms")
        .help("Request time in milliseconds.").register()

val MESSAGES_ON_INFOTRYGD_SMIKKEOK_QUEUE_COUNTER: Gauge = Gauge.Builder()
        .namespace(NAMESPACE)
        .name("smikkeok_gauge")
        .help("Registers a gauge for number of message on QUEUE")
        .register()
