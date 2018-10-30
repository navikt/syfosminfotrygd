package no.nav.syfo

import no.nav.model.infotrygdSporing.InfotrygdForesp
import no.nav.model.infotrygdSporing.StatusType
import no.nav.model.infotrygdSporing.TypeSMinfo
import no.nav.model.sm2013.HelseOpplysningerArbeidsuforhet
import no.nav.syfo.rules.postInfotrygdQueryChain
import org.amshove.kluent.shouldEqual
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.util.GregorianCalendar
import javax.xml.datatype.DatatypeFactory

object PostInfotrygdForespSpek : Spek({
    val datatypeFactory: DatatypeFactory = DatatypeFactory.newInstance()

    fun deafaultHelseOpplysningerArbeidsuforhet() = HelseOpplysningerArbeidsuforhet().apply {
        aktivitet = HelseOpplysningerArbeidsuforhet.Aktivitet().apply {
        }
    }

    fun deafaultInfotrygdForesp() = InfotrygdForesp().apply {
        pasient = InfotrygdForesp.Pasient().apply {
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

    describe("Testing infotrygd rules and checking the outcome") {
        it("Should check rule 1501") {
            val healthInformation = deafaultHelseOpplysningerArbeidsuforhet()
            val infotrygdForespResponse = deafaultInfotrygdForesp()
            infotrygdForespResponse.pasient.isFinnes = false

            val results = listOf(
                    postInfotrygdQueryChain.executeFlow(InfotrygdForespAndHealthInformation(infotrygdForespResponse, healthInformation))
            ).flatMap { it }

            results.any { it.outcomeType.ruleId == 1501 } shouldEqual true
            }

        it("Should check rule 1510") {
            val healthInformation = deafaultHelseOpplysningerArbeidsuforhet()
            healthInformation.syketilfelleStartDato = datatypeFactory.newXMLGregorianCalendar(GregorianCalendar())
            healthInformation.aktivitet.periode.add(HelseOpplysningerArbeidsuforhet.Aktivitet.Periode().apply {
                periodeFOMDato = datatypeFactory.newXMLGregorianCalendar(GregorianCalendar())
                periodeFOMDato.year = healthInformation.syketilfelleStartDato.year - 1
            })

            val infotrygdForespResponse = deafaultInfotrygdForesp()
            infotrygdForespResponse.sMhistorikk.status.kodeMelding = "00"

            val results = listOf(
                    postInfotrygdQueryChain.executeFlow(InfotrygdForespAndHealthInformation(infotrygdForespResponse, healthInformation))
            ).flatMap { it }

            results.any { it.outcomeType.ruleId == 1510 } shouldEqual true
        }

        it("Should check rule 1513") {
            val healthInformation = deafaultHelseOpplysningerArbeidsuforhet()
            healthInformation.aktivitet.periode.add(HelseOpplysningerArbeidsuforhet.Aktivitet.Periode().apply {
                periodeFOMDato = datatypeFactory.newXMLGregorianCalendar(GregorianCalendar())
            })

            val infotrygdForespResponse = deafaultInfotrygdForesp()
            infotrygdForespResponse.sMhistorikk.sykmelding.add(TypeSMinfo().apply {
                periode = TypeSMinfo.Periode().apply {
                    arbufoerFOM = datatypeFactory.newXMLGregorianCalendar(GregorianCalendar())
                    arbufoerFOM.year = healthInformation.aktivitet.periode.first().periodeFOMDato.year - 1
                }
            })

            val results = listOf(
                    postInfotrygdQueryChain.executeFlow(InfotrygdForespAndHealthInformation(infotrygdForespResponse, healthInformation))
            ).flatMap { it }

            results.any { it.outcomeType.ruleId == 1513 } shouldEqual true
        }

        it("Should check rule 1514") {
            val healthInformation = deafaultHelseOpplysningerArbeidsuforhet()

            val infotrygdForespResponse = deafaultInfotrygdForesp()
            infotrygdForespResponse.sMhistorikk.sykmelding.add(TypeSMinfo().apply {
                periode = TypeSMinfo.Periode().apply {
                    arbufoerFOM = datatypeFactory.newXMLGregorianCalendar(GregorianCalendar(2018, 0, 1))
                    arbufoerTOM = datatypeFactory.newXMLGregorianCalendar(GregorianCalendar(2018, 11, 31))
                }
            })

            val results = listOf(
                    postInfotrygdQueryChain.executeFlow(InfotrygdForespAndHealthInformation(infotrygdForespResponse, healthInformation))
            ).flatMap { it }

            results.any { it.outcomeType.ruleId == 1514 } shouldEqual true
        }

        it("Should check rule 1515") {
            val healthInformation = deafaultHelseOpplysningerArbeidsuforhet()
            healthInformation.aktivitet.periode.add(HelseOpplysningerArbeidsuforhet.Aktivitet.Periode().apply {
                periodeFOMDato = datatypeFactory.newXMLGregorianCalendar(GregorianCalendar(2018, 0, 1))
            })

            val infotrygdForespResponse = deafaultInfotrygdForesp()
            infotrygdForespResponse.sMhistorikk.sykmelding.add(TypeSMinfo().apply {
                periode = TypeSMinfo.Periode().apply {
                    arbufoerFOM = datatypeFactory.newXMLGregorianCalendar(GregorianCalendar(2018, 0, 1))
                    friskKode = "J"
                }
            })
            infotrygdForespResponse.sMhistorikk.sykmelding.add(TypeSMinfo().apply {
                periode = TypeSMinfo.Periode().apply {
                    utbetTOM = datatypeFactory.newXMLGregorianCalendar(GregorianCalendar(2018, 0, 1))
                    arbufoerFOM = datatypeFactory.newXMLGregorianCalendar(GregorianCalendar(2017, 0, 1))
                }
            })

            val results = listOf(
                    postInfotrygdQueryChain.executeFlow(InfotrygdForespAndHealthInformation(infotrygdForespResponse, healthInformation))
            ).flatMap { it }

            results.any { it.outcomeType.ruleId == 1515 } shouldEqual true
        }

        it("Should check rule 1517") {
            val healthInformation = deafaultHelseOpplysningerArbeidsuforhet()

            val infotrygdForespResponse = deafaultInfotrygdForesp()
            infotrygdForespResponse.sMhistorikk.sykmelding.add(TypeSMinfo().apply {
                periode = TypeSMinfo.Periode().apply {
                    friskmeldtDato = datatypeFactory.newXMLGregorianCalendar(GregorianCalendar(2017, 0, 1))
                    utbetTOM = datatypeFactory.newXMLGregorianCalendar(GregorianCalendar(2018, 0, 1))
                }
            })

            val results = listOf(
                    postInfotrygdQueryChain.executeFlow(InfotrygdForespAndHealthInformation(infotrygdForespResponse, healthInformation))
            ).flatMap { it }

            results.any { it.outcomeType.ruleId == 1517 } shouldEqual true
        }

        it("Should check rule 1518") {
            val healthInformation = deafaultHelseOpplysningerArbeidsuforhet()

            val infotrygdForespResponse = deafaultInfotrygdForesp()
            infotrygdForespResponse.sMhistorikk.sykmelding.add(TypeSMinfo().apply {
                periode = TypeSMinfo.Periode().apply {
                    friskmeldtDato = datatypeFactory.newXMLGregorianCalendar(GregorianCalendar(2018, 0, 1))
                }
            })
            infotrygdForespResponse.sMhistorikk.sykmelding.add(TypeSMinfo().apply {
                periode = TypeSMinfo.Periode().apply {
                    friskmeldtDato = datatypeFactory.newXMLGregorianCalendar(GregorianCalendar(2017, 0, 1))
                }
            })

            val results = listOf(
                    postInfotrygdQueryChain.executeFlow(InfotrygdForespAndHealthInformation(infotrygdForespResponse, healthInformation))
            ).flatMap { it }

            results.any { it.outcomeType.ruleId == 1518 } shouldEqual true
        }

        it("Should check rule 1530") {
            val healthInformation = deafaultHelseOpplysningerArbeidsuforhet()
            healthInformation.aktivitet.periode.add(HelseOpplysningerArbeidsuforhet.Aktivitet.Periode().apply {
                gradertSykmelding = HelseOpplysningerArbeidsuforhet.Aktivitet.Periode.GradertSykmelding().apply {
                    sykmeldingsgrad = 100
                }
                periodeFOMDato = datatypeFactory.newXMLGregorianCalendar(GregorianCalendar(2017, 0, 1))
                periodeTOMDato = datatypeFactory.newXMLGregorianCalendar(GregorianCalendar(2018, 1, 1))
            })

            val infotrygdForespResponse = deafaultInfotrygdForesp()
            infotrygdForespResponse.sMhistorikk.sykmelding.add(TypeSMinfo().apply {
                periode = TypeSMinfo.Periode().apply {
                    ufoeregrad = "90".toBigInteger()
                    arbufoerFOM = datatypeFactory.newXMLGregorianCalendar(GregorianCalendar(2018, 0, 1))
                }
            })

            val results = listOf(
                    postInfotrygdQueryChain.executeFlow(InfotrygdForespAndHealthInformation(infotrygdForespResponse, healthInformation))
            ).flatMap { it }

            results.any { it.outcomeType.ruleId == 1530 } shouldEqual true
        }

        it("Should check rule 1540") {
            val healthInformation = deafaultHelseOpplysningerArbeidsuforhet()

            val infotrygdForespResponse = deafaultInfotrygdForesp()
            infotrygdForespResponse.diagnosekodeOK.isDiagnoseOk = false

            val results = listOf(
                    postInfotrygdQueryChain.executeFlow(InfotrygdForespAndHealthInformation(infotrygdForespResponse, healthInformation))
            ).flatMap { it }

            results.any { it.outcomeType.ruleId == 1540 } shouldEqual true
        }

        it("Should check rule 1544") {
            val healthInformation = deafaultHelseOpplysningerArbeidsuforhet()
            healthInformation.aktivitet.periode.add(HelseOpplysningerArbeidsuforhet.Aktivitet.Periode().apply {
                periodeFOMDato = datatypeFactory.newXMLGregorianCalendar(GregorianCalendar(2018, 0, 1))
            })

            val infotrygdForespResponse = deafaultInfotrygdForesp()
            infotrygdForespResponse.sMhistorikk.sykmelding.add(TypeSMinfo().apply {
                historikk.add(TypeSMinfo.Historikk().apply {
                    tilltak = TypeSMinfo.Historikk.Tilltak().apply {
                        type = "FA"
                        tom = datatypeFactory.newXMLGregorianCalendar(GregorianCalendar(2017, 0, 1))
                    }
                })
            })

            val results = listOf(
                    postInfotrygdQueryChain.executeFlow(InfotrygdForespAndHealthInformation(infotrygdForespResponse, healthInformation))
            ).flatMap { it }

            results.any { it.outcomeType.ruleId == 1544 } shouldEqual true
        }

        it("Should check rule 1545") {
            val healthInformation = deafaultHelseOpplysningerArbeidsuforhet()

            val infotrygdForespResponse = deafaultInfotrygdForesp()
            infotrygdForespResponse.sMhistorikk.sykmelding.add(TypeSMinfo().apply {
                periode = TypeSMinfo.Periode().apply {
                    stans = "DØD"
                }
            })

            val results = listOf(
                    postInfotrygdQueryChain.executeFlow(InfotrygdForespAndHealthInformation(infotrygdForespResponse, healthInformation))
            ).flatMap { it }

            results.any { it.outcomeType.ruleId == 1545 } shouldEqual true
        }

        it("Should check rule 1546") {
            val healthInformation = deafaultHelseOpplysningerArbeidsuforhet()

            val infotrygdForespResponse = deafaultInfotrygdForesp()
            infotrygdForespResponse.sMhistorikk.sykmelding.add(TypeSMinfo().apply {
                periode = TypeSMinfo.Periode().apply {
                    stans = "FL"
                }
            })

            val results = listOf(
                    postInfotrygdQueryChain.executeFlow(InfotrygdForespAndHealthInformation(infotrygdForespResponse, healthInformation))
            ).flatMap { it }

            results.any { it.outcomeType.ruleId == 1546 } shouldEqual true
        }
    }
})
