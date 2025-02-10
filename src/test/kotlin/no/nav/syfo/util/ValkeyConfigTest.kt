package no.nav.syfo.util

import io.kotest.core.spec.style.FunSpec
import org.amshove.kluent.shouldBeEqualTo

class ValkeyConfigTest :
    FunSpec({
        test("Test config") {
            val valkeyConfig =
                ValkeyConfig(
                    host = "valkey-teamsykmelding-infotrygd.no",
                    port = 12345,
                    username = "Username",
                    password = "Password",
                )

            valkeyConfig.host shouldBeEqualTo "valkey-teamsykmelding-infotrygd.no"
            valkeyConfig.port shouldBeEqualTo 12345
        }
    })
