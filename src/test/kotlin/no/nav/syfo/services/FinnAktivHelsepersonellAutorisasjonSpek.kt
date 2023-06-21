package no.nav.syfo.services

import io.kotest.core.spec.style.FunSpec
import no.nav.syfo.client.Behandler
import no.nav.syfo.client.Godkjenning
import no.nav.syfo.client.Kode
import no.nav.syfo.model.HelsepersonellKategori
import org.amshove.kluent.shouldBeEqualTo

class FinnAktivHelsepersonellAutorisasjonSpek :
    FunSpec({
        context("Tester at man finner riktig helsepersonell autorisasjoner verdi") {
            test("Sjekker at man velger Lege verdien dersom fleire helsepersonell autorisasjoner") {
                val helsepersonelPerson =
                    Behandler(
                        listOf(
                            Godkjenning(
                                autorisasjon =
                                    Kode(
                                        aktiv = true,
                                        oid = 0,
                                        verdi = "",
                                    ),
                                helsepersonellkategori =
                                    Kode(
                                        aktiv = true,
                                        oid = 0,
                                        verdi = HelsepersonellKategori.KIROPRAKTOR.verdi,
                                    ),
                            ),
                            Godkjenning(
                                autorisasjon =
                                    Kode(
                                        aktiv = true,
                                        oid = 0,
                                        verdi = "",
                                    ),
                                helsepersonellkategori =
                                    Kode(
                                        aktiv = true,
                                        oid = 0,
                                        verdi = HelsepersonellKategori.LEGE.verdi,
                                    ),
                            ),
                        ),
                    )

                finnAktivHelsepersonellAutorisasjons(helsepersonelPerson) shouldBeEqualTo
                    HelsepersonellKategori.LEGE.verdi
            }

            test(
                "Sjekker at man velger Kiropraktor verdien dersom dei andre helsepersonell autorisasjoner er inaktiv"
            ) {
                val helsepersonelPerson =
                    Behandler(
                        listOf(
                            Godkjenning(
                                autorisasjon =
                                    Kode(
                                        aktiv = true,
                                        oid = 0,
                                        verdi = "",
                                    ),
                                helsepersonellkategori =
                                    Kode(
                                        aktiv = true,
                                        oid = 0,
                                        verdi = HelsepersonellKategori.KIROPRAKTOR.verdi,
                                    ),
                            ),
                            Godkjenning(
                                autorisasjon =
                                    Kode(
                                        aktiv = true,
                                        oid = 0,
                                        verdi = "",
                                    ),
                                helsepersonellkategori =
                                    Kode(
                                        aktiv = false,
                                        oid = 0,
                                        verdi = HelsepersonellKategori.LEGE.verdi,
                                    ),
                            ),
                        ),
                    )

                finnAktivHelsepersonellAutorisasjons(helsepersonelPerson) shouldBeEqualTo
                    HelsepersonellKategori.KIROPRAKTOR.verdi
            }

            test(
                "Sjekker at man velger tomt verdi dersom ingen er aktive helsepersonellkategori verdier"
            ) {
                val helsepersonelPerson =
                    Behandler(
                        listOf(
                            Godkjenning(
                                autorisasjon =
                                    Kode(
                                        aktiv = true,
                                        oid = 0,
                                        verdi = "",
                                    ),
                                helsepersonellkategori =
                                    Kode(
                                        aktiv = false,
                                        oid = 0,
                                        verdi = HelsepersonellKategori.KIROPRAKTOR.verdi,
                                    ),
                            ),
                            Godkjenning(
                                autorisasjon =
                                    Kode(
                                        aktiv = true,
                                        oid = 0,
                                        verdi = "",
                                    ),
                                helsepersonellkategori =
                                    Kode(
                                        aktiv = false,
                                        oid = 0,
                                        verdi = HelsepersonellKategori.LEGE.verdi,
                                    ),
                            ),
                        ),
                    )

                finnAktivHelsepersonellAutorisasjons(helsepersonelPerson) shouldBeEqualTo ""
            }

            test("Sjekker at man velger tomt verdi dersom det er ingen godkjenninger") {
                val helsepersonelPerson = Behandler(emptyList())

                finnAktivHelsepersonellAutorisasjons(helsepersonelPerson) shouldBeEqualTo ""
            }
        }
    })
