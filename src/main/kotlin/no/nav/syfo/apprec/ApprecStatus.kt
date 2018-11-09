package no.nav.syfo.apprec

enum class ApprecStatus(val v: String, val dn: String) {
    avvist("2", "Avvist"),
    ok("1", "OK")
}
