package no.nav.syfo.datagen

import no.nav.syfo.OutcomeType
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.nio.file.Files
import java.nio.file.Paths

object OutcomeTypeCsvGeneratorSpek : Spek({
    describe("OutcomeType") {
            val basePath = Paths.get("build/doc")
            Files.createDirectories(basePath)
            val header = "Referanse i kode;Merknad nr;Utfall"
            val csv = OutcomeType.values()
                    .map { "${it.name};${it.ruleId};${it.status}" }
            val csvFile = basePath.resolve("rules.csv")
            Files.write(csvFile, listOf(header) + csv, Charsets.UTF_8)
        }
})
