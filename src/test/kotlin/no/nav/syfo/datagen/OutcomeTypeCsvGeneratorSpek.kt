package no.nav.syfo.datagen

import no.nav.syfo.OutcomeType
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.nio.file.Files
import java.nio.file.Paths

object OutcomeTypeCsvGeneratorSpek : Spek({
    describe("OutcomeType") {
        it("Generates a OutcomeType CSV") {
            val header = "Referanse i kode;Merknad nr;Utfall"
            val csv = OutcomeType.values()
                    .map { "${it.name};${it.ruleId};${it.status}" }
            val basePath = Paths.get("build", "reports")
            Files.createDirectories(basePath)
            Files.write(basePath.resolve("rules.csv"), listOf(header) + csv, Charsets.UTF_8)
        }
    }
})
