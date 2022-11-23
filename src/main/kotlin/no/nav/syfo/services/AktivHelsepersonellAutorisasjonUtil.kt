package no.nav.syfo.services

import no.nav.syfo.client.Behandler
import no.nav.syfo.client.Godkjenning
import no.nav.syfo.model.HelsepersonellKategori

fun finnAktivHelsepersonellAutorisasjons(helsepersonelPerson: Behandler): String {
    val godkjenteHelsepersonellAutorisasjonsAktiv = godkjenteHelsepersonellAutorisasjonsAktiv(helsepersonelPerson)
    if (godkjenteHelsepersonellAutorisasjonsAktiv.isEmpty()) {
        return ""
    }
    return when (
        helsepersonellGodkjenningSom(
            godkjenteHelsepersonellAutorisasjonsAktiv,
            listOf(
                HelsepersonellKategori.LEGE.verdi
            )
        )
    ) {
        true -> HelsepersonellKategori.LEGE.verdi
        else -> godkjenteHelsepersonellAutorisasjonsAktiv.firstOrNull()?.helsepersonellkategori?.verdi ?: ""
    }
}

private fun godkjenteHelsepersonellAutorisasjonsAktiv(helsepersonelPerson: Behandler): List<Godkjenning> =
    helsepersonelPerson.godkjenninger.filter { godkjenning ->
        godkjenning.helsepersonellkategori?.aktiv != null &&
            godkjenning.autorisasjon?.aktiv == true &&
            godkjenning.helsepersonellkategori.verdi != null &&
            godkjenning.helsepersonellkategori.aktiv
    }

private fun helsepersonellGodkjenningSom(helsepersonellGodkjenning: List<Godkjenning>, helsepersonerVerdi: List<String>): Boolean =
    helsepersonellGodkjenning.any { godkjenning ->
        godkjenning.helsepersonellkategori.let { kode ->
            kode?.verdi in helsepersonerVerdi
        }
    }
