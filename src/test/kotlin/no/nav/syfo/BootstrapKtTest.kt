package no.nav.syfo

import org.amshove.kluent.shouldEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.OffsetTime
import java.time.ZoneOffset

class BootstrapKtTest : Spek({
    describe("Test runtime") {
        it("Should give true at 20:59") {
            shouldRun(OffsetTime.of(20, 59, 0, 0, ZoneOffset.of("+02:00"))) shouldEqualTo true
        }
        it("Should give false at 21:00") {
            shouldRun(OffsetTime.of(21, 0, 0, 0, ZoneOffset.of("+02:00"))) shouldEqualTo false
        }
        it("Should give true at 05:00") {
            shouldRun(OffsetTime.of(5, 0, 0, 0, ZoneOffset.of("+02:00"))) shouldEqualTo true
        }
        it("Should give false at 04:59") {
            shouldRun(OffsetTime.of(4, 59, 0, 0, ZoneOffset.of("+02:00"))) shouldEqualTo false
        }
    }
})
