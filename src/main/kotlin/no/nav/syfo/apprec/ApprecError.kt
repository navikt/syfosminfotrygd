package no.nav.syfo.apprec

enum class ApprecError(val v: String, val dn: String, val s: String) {
    DUPLICATE("54", "Duplikat! - Denne legeerklæringen meldingen er mottatt tidligere. Skal ikke sendes på nytt.",
            "2.16.578.1.12.4.1.1.8222"),
}
