package no.nav.syfo

import io.prometheus.client.Counter

const val NAMESPACE = "syfosminfotrygd"

val RULE_HIT_COUNTER: Counter = Counter.Builder().namespace(NAMESPACE).name("rule_hit_counter")
        .labelNames("rule_name").help("Registers a counter for each rule in the rule set").register()
