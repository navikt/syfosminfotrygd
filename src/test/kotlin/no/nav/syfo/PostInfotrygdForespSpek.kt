package no.nav.syfo

import no.nav.model.infotrygdSporing.InfotrygdForesp
import no.nav.model.infotrygdSporing.StatusType
import no.nav.model.sm2013.HelseOpplysningerArbeidsuforhet
import no.nav.syfo.rules.postInfotrygdQueryChain
import org.amshove.kluent.shouldEqual
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object PostInfotrygdForespSpek : Spek({
    describe("Testing infotrygd rules and checking the outcome") {
            it("Should check rule 1501") {
                val healthInformation = HelseOpplysningerArbeidsuforhet().apply {
                    aktivitet = HelseOpplysningerArbeidsuforhet.Aktivitet().apply {
                    }
                }

                val infotrygdForespResponse = InfotrygdForesp().apply {
                    pasient = InfotrygdForesp.Pasient().apply {
                        isFinnes = false
                    }
                    sMhistorikk = InfotrygdForesp.SMhistorikk().apply {
                        status = StatusType().apply {
                            kodeMelding = "00"
                        }
                    }
                    hovedStatus = StatusType().apply {
                        kodeMelding = "00"
                    }
                    diagnosekodeOK = InfotrygdForesp.DiagnosekodeOK().apply {
                        isDiagnoseOk = true
                    }
                }

                val results = listOf(
                        postInfotrygdQueryChain.executeFlow(InfotrygdForespAndHealthInformation(infotrygdForespResponse, healthInformation))
                ).flatMap { it }

                results.first().outcomeType.ruleId.shouldEqual(1501)
            }
        }
})
