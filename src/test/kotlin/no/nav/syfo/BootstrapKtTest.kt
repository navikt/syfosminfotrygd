package no.nav.syfo

import io.kotest.core.spec.style.FunSpec
import java.time.LocalDate
import java.time.OffsetTime
import java.time.ZoneOffset
import org.amshove.kluent.shouldBeEqualTo

class BootstrapKtTest :
    FunSpec({
        context("Test runtime") {
            test("Should give true at 20:59") {
                shouldRun(
                    OffsetTime.of(20, 59, 0, 0, ZoneOffset.of("+02:00")).atDate(LocalDate.now())
                ) shouldBeEqualTo true
            }
            test("Should give false at 21:00") {
                shouldRun(
                    OffsetTime.of(21, 0, 0, 0, ZoneOffset.of("+02:00")).atDate(LocalDate.now())
                ) shouldBeEqualTo false
            }
            test("Should give true at 05:00") {
                shouldRun(
                    OffsetTime.of(5, 0, 0, 0, ZoneOffset.of("+02:00")).atDate(LocalDate.now())
                ) shouldBeEqualTo true
            }
            test("Should give false at 04:59") {
                shouldRun(
                    OffsetTime.of(4, 59, 0, 0, ZoneOffset.of("+02:00")).atDate(LocalDate.now())
                ) shouldBeEqualTo false
            }
        }
    })
