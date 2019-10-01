package no.nav.syfo.model

enum class HelsepersonellKategori(val beskrivendeHelsepersonellKategoriKategoriVerdi: String, val verdi: String) {
    KIROPRAKTOR("Kiropraktor", "KI"),
    LEGE("Lege", "LE"),
    FYSIOTERAPAEUT("Fysioterapeut", "FT"),
    TANNLEGE("Tannlege", "TL")
}
