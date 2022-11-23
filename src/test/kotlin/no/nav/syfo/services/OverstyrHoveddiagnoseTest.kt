package no.nav.syfo.services

import io.kotest.core.spec.style.FunSpec
import no.nav.helse.sm2013.ArsakType
import no.nav.helse.sm2013.CS
import no.nav.syfo.createDefaultHealthInformation
import org.amshove.kluent.shouldBeEqualTo

class OverstyrHoveddiagnoseTest : FunSpec({
    context("setHovedDiagnoseToA99IfhovedDiagnoseIsNullAndAnnenFraversArsakIsSet") {
        test("Should set hoveddiagnose to A99 when hovedDiagnose is null and annenFraversArsak is not null ") {
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

        test("Should set not hoveddiagnose to A99 when hovedDiagnose is not null and annenFraversArsak is not null ") {
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
