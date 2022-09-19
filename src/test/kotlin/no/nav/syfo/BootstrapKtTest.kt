package no.nav.syfo

import io.kotest.core.spec.style.FunSpec
import no.nav.helse.sm2013.ArsakType
import no.nav.helse.sm2013.CS
import org.amshove.kluent.shouldBeEqualTo
import java.time.OffsetTime
import java.time.ZoneOffset

class BootstrapKtTest : FunSpec({
    context("Test runtime") {
        test("Should give true at 20:59") {
            shouldRun(OffsetTime.of(20, 59, 0, 0, ZoneOffset.of("+02:00"))) shouldBeEqualTo true
        }
        test("Should give false at 21:00") {
            shouldRun(OffsetTime.of(21, 0, 0, 0, ZoneOffset.of("+02:00"))) shouldBeEqualTo false
        }
        test("Should give true at 05:00") {
            shouldRun(OffsetTime.of(5, 0, 0, 0, ZoneOffset.of("+02:00"))) shouldBeEqualTo true
        }
        test("Should give false at 04:59") {
            shouldRun(OffsetTime.of(4, 59, 0, 0, ZoneOffset.of("+02:00"))) shouldBeEqualTo false
        }

        test("Should set houved diagnose to A99 when hovedDiagnose is null and annenFraversArsak is not null ") {
            val defaultHealthInformation = createDefaultHealthInformation()
            defaultHealthInformation.medisinskVurdering.hovedDiagnose = null
            defaultHealthInformation.medisinskVurdering.annenFraversArsak = ArsakType().apply {
                arsakskode.add(
                    CS().apply {
                        v = "2"
                        dn =
                            "Når vedkommende er under behandling og legen erklærer at behandlingen gjør det" +
                            " nødvendig at vedkommende ikke arbeider"
                    }
                )
            }
            val healthInformation = setHovedDiagnoseToA99IfhovedDiagnoseIsNullAndAnnenFraversArsakIsSet(defaultHealthInformation)

            healthInformation.medisinskVurdering.hovedDiagnose.diagnosekode.v shouldBeEqualTo "A99"
        }

        test("Should set not houved diagnose to A99 when hovedDiagnose is not null and annenFraversArsak is not null ") {
            val defaultHealthInformation = createDefaultHealthInformation()
            defaultHealthInformation.medisinskVurdering.annenFraversArsak = ArsakType().apply {
                arsakskode.add(
                    CS().apply {
                        v = "2"
                        dn =
                            "Når vedkommende er under behandling og legen erklærer at behandlingen gjør det" +
                            " nødvendig at vedkommende ikke arbeider"
                    }
                )
            }
            val healthInformation = setHovedDiagnoseToA99IfhovedDiagnoseIsNullAndAnnenFraversArsakIsSet(defaultHealthInformation)

            healthInformation.medisinskVurdering.hovedDiagnose.diagnosekode.v shouldBeEqualTo "Z09"
        }
    }
})
