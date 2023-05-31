package no.nav.syfo.pdl.service

import io.kotest.core.spec.style.FunSpec
import no.nav.syfo.pdl.client.model.Kontaktadresse
import org.amshove.kluent.shouldBeEqualTo
import java.time.LocalDateTime

class PdlPersonServiceKtTest : FunSpec({
    test("Should not fail when adresse is fraOgMed is null") {
        val kontaktaddresser = listOf(
            Kontaktadresse(
                type = "Utland",
                gyldigFraOgMed = null,
                gyldigTilOgMed = null,
            ),
            Kontaktadresse(
                "Innland",
                LocalDateTime.now().toString(),
                null,
            ),
        )

        val utland = sisteKontaktAdresseIUtlandet(kontaktaddresser)
        utland shouldBeEqualTo false
    }
})
