package no.nav.syfo.util

import no.nav.helse.eiFellesformat.XMLEIFellesformat
import no.nav.syfo.getFileAsString
import org.amshove.kluent.shouldBeTrue
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.io.StringReader

object JaxBUtilsSpek : Spek({

    describe("JaxBUtilsSpek") {
        it("Escaper krokodiller i verdier") {
            val stringInput = getFileAsString("src/test/resources/sykemeldingMedSpesialtegn.xml")
            val fellesformat = fellesformatUnmarshaller
                .unmarshal(StringReader(stringInput)) as XMLEIFellesformat

            val escapetXMLSomString = xmlObjectWriter.writeValueAsString(fellesformat)

            escapetXMLSomString.contains("<TeleAddress V=\"Tlf:&lt;Ikkespesifisert&gt;\"/>").shouldBeTrue()
            escapetXMLSomString.contains(
                "<Beskriv>&lt;&lt;&lt;VENSTRE KNE 5mm beinmargsOdem i mediale tibiakondylen med litt intrameniskeal degenerasjon i bakhornet av mediale menisken&gt;&gt;&gt; og &lt;&lt;&lt; HOYRE KNE Minimal degenerasjon i mediale menisken utan gjennomgaande ruptur og med liten skvett hydrops&gt;&gt;&gt;" +
                    " sterke smerte ved belastning av begge knaer. Kan ikke gar pa flaten.</Beskriv>"
            ).shouldBeTrue()

            escapetXMLSomString.contains("Diagnosekode V=\"W05\" S=\"2.16.578.1.12.4.1.1.7170\" DN=\"KVALME SVANGERSKAP (&gt;22 UKER)\"/>").shouldBeTrue()
        }
    }
})
