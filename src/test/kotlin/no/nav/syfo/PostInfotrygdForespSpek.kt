package no.nav.syfo

import no.nav.model.infotrygdSporing.InfotrygdForesp
import no.nav.model.infotrygdSporing.StatusType
import no.nav.model.infotrygdSporing.TypeMottakerKode
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

            results.any { it.outcomeType == OutcomeType.PATIENT_NOT_IN_IP } shouldEqual true
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

            results.any { it.outcomeType == OutcomeType.MESSAGE_NOT_IN_INFOTRYGD } shouldEqual true
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

            results.any { it.outcomeType == OutcomeType.PARTIALLY_COINCIDENT_SICK_LEAVE_PERIOD_WITH_PREVIOUSLY_REGISTERED_SICK_LEAVE } shouldEqual true
        }

        it("Should check rule 1514") {
            val healthInformation = deafaultHelseOpplysningerArbeidsuforhet()

            val infotrygdForespResponse = deafaultInfotrygdForesp()
            infotrygdForespResponse.sMhistorikk.sykmelding.add(TypeSMinfo().apply {
                periode = TypeSMinfo.Periode().apply {
                    arbufoerFOM = datatypeFactory.newXMLGregorianCalendar(GregorianCalendar(2018, 5, 1))
                    arbufoerTOM = datatypeFactory.newXMLGregorianCalendar(GregorianCalendar(2019, 6, 31))
                }
            })

            val results = listOf(
                    postInfotrygdQueryChain.executeFlow(InfotrygdForespAndHealthInformation(infotrygdForespResponse, healthInformation))
            ).flatMap { it }

            results.any { it.outcomeType == OutcomeType.SICK_LEAVE_PERIOD_OVER_1_YEAR } shouldEqual true
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

            results.any { it.outcomeType == OutcomeType.SICKLEAVE_EXTENTION_FROM_DIFFRENT_NAV_OFFICE } shouldEqual true
        }

        it("Should check rule 1516") {
            val healthInformation = deafaultHelseOpplysningerArbeidsuforhet()

            val infotrygdForespResponse = deafaultInfotrygdForesp()
            infotrygdForespResponse.sMhistorikk.sykmelding.add(TypeSMinfo().apply {
                periode = TypeSMinfo.Periode().apply {
                    friskmeldtDato = datatypeFactory.newXMLGregorianCalendar(GregorianCalendar(2017, 0, 1))
                    arbufoerTOM = datatypeFactory.newXMLGregorianCalendar(GregorianCalendar(2018, 0, 1))
                }
            })

            val results = listOf(
                    postInfotrygdQueryChain.executeFlow(InfotrygdForespAndHealthInformation(infotrygdForespResponse, healthInformation))
            ).flatMap { it }

            results.any { it.outcomeType == OutcomeType.NEW_CLEAN_BILL_DATE_BEFORE_ARBUFORTOM } shouldEqual true
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

            results.any { it.outcomeType == OutcomeType.NEW_CLEAN_BILL_DATE_BEFORE_PAYOUT } shouldEqual true
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

            results.any { it.outcomeType == OutcomeType.NEW_CLEAN_BILL_DATE_BEFORE_REGISTERD_CLEAN_BILL_DATE } shouldEqual true
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

            results.any { it.outcomeType == OutcomeType.DIABILITY_GRADE_CANGED } shouldEqual true
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

            results.any { it.outcomeType == OutcomeType.EXTANION_OVER_FA } shouldEqual true
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

            results.any { it.outcomeType == OutcomeType.PATIENT_DEAD } shouldEqual true
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

            results.any { it.outcomeType == OutcomeType.PERSON_MOVING_KODE_FL } shouldEqual true
        }

        it("Should check rule 1548") {
            val healthInformation = deafaultHelseOpplysningerArbeidsuforhet()

            val infotrygdForespResponse = deafaultInfotrygdForesp()
            infotrygdForespResponse.sMhistorikk.sykmelding.add(TypeSMinfo().apply {
                periode = TypeSMinfo.Periode().apply {
                    stans = "AD"
                }
            })

            val results = listOf(
                    postInfotrygdQueryChain.executeFlow(InfotrygdForespAndHealthInformation(infotrygdForespResponse, healthInformation))
            ).flatMap { it }

            results.any { it.outcomeType == OutcomeType.PERIOD_ENDED_DEAD } shouldEqual true
        }

        it("Should check rule 1549") {
            val healthInformation = deafaultHelseOpplysningerArbeidsuforhet()

            val infotrygdForespResponse = deafaultInfotrygdForesp()
            infotrygdForespResponse.sMhistorikk.sykmelding.add(TypeSMinfo().apply {
                periode = TypeSMinfo.Periode().apply {
                    stans = "AA"
                }
            })

            val results = listOf(
                    postInfotrygdQueryChain.executeFlow(InfotrygdForespAndHealthInformation(infotrygdForespResponse, healthInformation))
            ).flatMap { it }

            results.any { it.outcomeType == OutcomeType.PERIOD_FOR_AA_ENDED } shouldEqual true
        }

        it("Should check rule 1550") {
            val healthInformation = deafaultHelseOpplysningerArbeidsuforhet()

            val infotrygdForespResponse = deafaultInfotrygdForesp()
            infotrygdForespResponse.sMhistorikk.sykmelding.add(TypeSMinfo().apply {
                periode = TypeSMinfo.Periode().apply {
                    stans = "AF"
                }
            })

            val results = listOf(
                    postInfotrygdQueryChain.executeFlow(InfotrygdForespAndHealthInformation(infotrygdForespResponse, healthInformation))
            ).flatMap { it }

            results.any { it.outcomeType == OutcomeType.PERIOD_IS_AF } shouldEqual true
        }

        it("Should check rule 1551") {
            val healthInformation = deafaultHelseOpplysningerArbeidsuforhet()

            val infotrygdForespResponse = deafaultInfotrygdForesp()
            infotrygdForespResponse.sMhistorikk.sykmelding.add(TypeSMinfo().apply {
                periode = TypeSMinfo.Periode().apply {
                    stans = "MAX"
                }
            })

            val results = listOf(
                    postInfotrygdQueryChain.executeFlow(InfotrygdForespAndHealthInformation(infotrygdForespResponse, healthInformation))
            ).flatMap { it }

            results.any { it.outcomeType == OutcomeType.MAX_SICK_LEAVE_PAYOUT } shouldEqual true
        }

        it("Should check rule 1552") {
            val healthInformation = deafaultHelseOpplysningerArbeidsuforhet()

            val infotrygdForespResponse = deafaultInfotrygdForesp()
            infotrygdForespResponse.sMhistorikk.sykmelding.add(TypeSMinfo().apply {
                periode = TypeSMinfo.Periode().apply {
                    avslag = "Fordi"
                }
            })

            val results = listOf(
                    postInfotrygdQueryChain.executeFlow(InfotrygdForespAndHealthInformation(infotrygdForespResponse, healthInformation))
            ).flatMap { it }

            results.any { it.outcomeType == OutcomeType.REFUSAL_IS_REGISTERED } shouldEqual true
        }

        it("Should check rule 1591") {
            val healthInformation = deafaultHelseOpplysningerArbeidsuforhet()

            val infotrygdForespResponse = deafaultInfotrygdForesp()
            infotrygdForespResponse.hovedStatus = StatusType().apply {
                kodeMelding = "3"
            }

            infotrygdForespResponse.sMhistorikk = InfotrygdForesp.SMhistorikk().apply {
                status = StatusType().apply {
                    kodeMelding = "3"
                }
            }

            infotrygdForespResponse.parallelleYtelser = InfotrygdForesp.ParallelleYtelser().apply {
                status = StatusType().apply {
                    kodeMelding = "3"
                }
            }

            infotrygdForespResponse.diagnosekodeOK = InfotrygdForesp.DiagnosekodeOK().apply {
                status = StatusType().apply {
                    kodeMelding = "3"
                }
            }

            infotrygdForespResponse.pasient = InfotrygdForesp.Pasient().apply {
                status = StatusType().apply {
                    kodeMelding = "5"
                }
            }

            val results = listOf(
                    postInfotrygdQueryChain.executeFlow(InfotrygdForespAndHealthInformation(infotrygdForespResponse, healthInformation))
            ).flatMap { it }

            results.any { it.outcomeType == OutcomeType.ERROR_FROM_IT } shouldEqual true
        }

        it("Should check rule 1519") {
            val healthInformation = deafaultHelseOpplysningerArbeidsuforhet()
            healthInformation.aktivitet.periode.add(HelseOpplysningerArbeidsuforhet.Aktivitet.Periode().apply {
                periodeFOMDato = datatypeFactory.newXMLGregorianCalendar(GregorianCalendar(2018, 0, 1))
                periodeTOMDato = datatypeFactory.newXMLGregorianCalendar(GregorianCalendar(2018, 10, 1))
            })

            val infotrygdForespResponse = deafaultInfotrygdForesp()
            infotrygdForespResponse.behandlerInfo = InfotrygdForesp.BehandlerInfo().apply {
                behandler.add(InfotrygdForesp.BehandlerInfo.Behandler().apply {
                    mottakerKode = TypeMottakerKode.MT
                })
            }

            val results = listOf(
                    postInfotrygdQueryChain.executeFlow(InfotrygdForespAndHealthInformation(infotrygdForespResponse, healthInformation))
            ).flatMap { it }

            results.any { it.outcomeType == OutcomeType.DOCTOR_IS_MT_AND_OVER_12_WEEKS } shouldEqual true
        }

        it("Should check rule 1520") {
            val healthInformation = deafaultHelseOpplysningerArbeidsuforhet()
            healthInformation.aktivitet.periode.add(HelseOpplysningerArbeidsuforhet.Aktivitet.Periode().apply {
                periodeFOMDato = datatypeFactory.newXMLGregorianCalendar(GregorianCalendar(2018, 0, 1))
                periodeTOMDato = datatypeFactory.newXMLGregorianCalendar(GregorianCalendar(2018, 10, 1))
            })

            val infotrygdForespResponse = deafaultInfotrygdForesp()
            infotrygdForespResponse.behandlerInfo = InfotrygdForesp.BehandlerInfo().apply {
                behandler.add(InfotrygdForesp.BehandlerInfo.Behandler().apply {
                    mottakerKode = TypeMottakerKode.KI
                })
            }

            val results = listOf(
                    postInfotrygdQueryChain.executeFlow(InfotrygdForespAndHealthInformation(infotrygdForespResponse, healthInformation))
            ).flatMap { it }

            results.any { it.outcomeType == OutcomeType.DOCTOR_IS_KI_AND_OVER_12_WEEKS } shouldEqual true
        }
    }
})
