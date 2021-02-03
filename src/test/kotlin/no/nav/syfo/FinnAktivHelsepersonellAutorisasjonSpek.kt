package no.nav.syfo

import io.ktor.util.KtorExperimentalAPI
import no.nav.syfo.client.Behandler
import no.nav.syfo.client.Godkjenning
import no.nav.syfo.client.Kode
import no.nav.syfo.model.HelsepersonellKategori
import no.nav.syfo.services.UpdateInfotrygdService
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

@KtorExperimentalAPI
object FinnAktivHelsepersonellAutorisasjonSpek : Spek({

    describe("Tester at man finner riktig helsepersonell autorisasjoner verdi") {
        val updateInfotrygdService = UpdateInfotrygdService()
        it("Sjekker at man velger Lege verdien dersom fleire helsepersonell autorisasjoner") {

            val helsepersonelPerson = Behandler(
                    listOf(
                            Godkjenning(
                                    autorisasjon = Kode(
                                            aktiv = true,
                                            oid = 0,
                                            verdi = ""
                                    ),
                                    helsepersonellkategori = Kode(
                                            aktiv = true,
                                            oid = 0,
                                            verdi = HelsepersonellKategori.KIROPRAKTOR.verdi
                                    )
                            ),
                            Godkjenning(
                            autorisasjon = Kode(
                                    aktiv = true,
                                    oid = 0,
                                    verdi = ""
                            ),
                            helsepersonellkategori = Kode(
                                    aktiv = true,
                                    oid = 0,
                                    verdi = HelsepersonellKategori.LEGE.verdi
                            )
                    )

                    )
            )

            updateInfotrygdService.finnAktivHelsepersonellAutorisasjons(helsepersonelPerson) shouldBeEqualTo HelsepersonellKategori.LEGE.verdi
        }

        it("Sjekker at man velger Kiropraktor verdien dersom dei andre helsepersonell autorisasjoner er inaktiv") {

            val helsepersonelPerson = Behandler(
                    listOf(
                            Godkjenning(
                                    autorisasjon = Kode(
                                            aktiv = true,
                                            oid = 0,
                                            verdi = ""
                                    ),
                                    helsepersonellkategori = Kode(
                                            aktiv = true,
                                            oid = 0,
                                            verdi = HelsepersonellKategori.KIROPRAKTOR.verdi
                                    )
                            ),
                            Godkjenning(
                                    autorisasjon = Kode(
                                            aktiv = true,
                                            oid = 0,
                                            verdi = ""
                                    ),
                                    helsepersonellkategori = Kode(
                                            aktiv = false,
                                            oid = 0,
                                            verdi = HelsepersonellKategori.LEGE.verdi
                                    )
                            )

                    )
            )

            updateInfotrygdService.finnAktivHelsepersonellAutorisasjons(helsepersonelPerson) shouldBeEqualTo HelsepersonellKategori.KIROPRAKTOR.verdi
        }

        it("Sjekker at man velger tomt verdi dersom ingen er aktive helsepersonellkategori verdier") {

            val helsepersonelPerson = Behandler(
                    listOf(
                            Godkjenning(
                                    autorisasjon = Kode(
                                            aktiv = true,
                                            oid = 0,
                                            verdi = ""
                                    ),
                                    helsepersonellkategori = Kode(
                                            aktiv = false,
                                            oid = 0,
                                            verdi = HelsepersonellKategori.KIROPRAKTOR.verdi
                                    )
                            ),
                            Godkjenning(
                                    autorisasjon = Kode(
                                            aktiv = true,
                                            oid = 0,
                                            verdi = ""
                                    ),
                                    helsepersonellkategori = Kode(
                                            aktiv = false,
                                            oid = 0,
                                            verdi = HelsepersonellKategori.LEGE.verdi
                                    )
                            )

                    )
            )

            updateInfotrygdService.finnAktivHelsepersonellAutorisasjons(helsepersonelPerson) shouldBeEqualTo ""
        }

        it("Sjekker at man velger tomt verdi dersom det er ingen godkjenninger") {

            val helsepersonelPerson = Behandler(emptyList())

            updateInfotrygdService.finnAktivHelsepersonellAutorisasjons(helsepersonelPerson) shouldBeEqualTo ""
        }
    }
})
