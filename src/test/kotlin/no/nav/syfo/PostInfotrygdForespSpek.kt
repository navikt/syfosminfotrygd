package no.nav.syfo

import no.nav.model.infotrygdSporing.InfotrygdForesp
import no.nav.model.infotrygdSporing.StatusType
import no.nav.model.infotrygdSporing.TypeSMinfo
import no.nav.model.sm2013.HelseOpplysningerArbeidsuforhet
import no.nav.syfo.rules.RuleData
import no.nav.syfo.rules.ValidationRules

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
        hovedStatus = StatusType().apply {
            kodeMelding = "00"
        }
    }

    describe("Testing infotrygd rules and checking the rule outcomes") {

        it("Should check rule 1501") {
            val healthInformation = deafaultHelseOpplysningerArbeidsuforhet()
            val infotrygdForespResponse = deafaultInfotrygdForesp()
            infotrygdForespResponse.pasient = InfotrygdForesp.Pasient().apply {
                isFinnes = false
            }

            val ruleData = RuleData(infotrygdForespResponse, healthInformation)
            val results = listOf<List<Rule<RuleData>>>(
                    ValidationRules.values().toList()
            ).flatten().filter { rule -> rule.predicate(ruleData) }

            results.any { it == ValidationRules.PATIENT_NOT_IN_IP } shouldEqual true
            }

        it("Should check rule 1510") {
            val healthInformation = deafaultHelseOpplysningerArbeidsuforhet()
            healthInformation.syketilfelleStartDato = datatypeFactory.newXMLGregorianCalendar(GregorianCalendar())
            healthInformation.aktivitet.periode.add(HelseOpplysningerArbeidsuforhet.Aktivitet.Periode().apply {
                periodeFOMDato = datatypeFactory.newXMLGregorianCalendar(GregorianCalendar(2018, 0, 1))
            })

            val infotrygdForespResponse = deafaultInfotrygdForesp()
            infotrygdForespResponse.sMhistorikk = InfotrygdForesp.SMhistorikk().apply {
                status = StatusType().apply {
                    kodeMelding = "04"
                }
                sykmelding.add(TypeSMinfo().apply {
                    periode = TypeSMinfo.Periode().apply {
                        arbufoerTOM = datatypeFactory.newXMLGregorianCalendar(GregorianCalendar(2018, 5, 1))
                    }
                })
            }

            val ruleData = RuleData(infotrygdForespResponse, healthInformation)
            val results = listOf<List<Rule<RuleData>>>(
                    ValidationRules.values().toList()
            ).flatten().filter { rule -> rule.predicate(ruleData) }

            results.any { it == ValidationRules.MESSAGE_NOT_IN_INFOTRYGD } shouldEqual true
        }

        it("Should check rule 1513") {

            val infotrygdForespResponse = deafaultInfotrygdForesp()
            infotrygdForespResponse.sMhistorikk = InfotrygdForesp.SMhistorikk().apply {
                sykmelding.add(TypeSMinfo().apply {
                    periode = TypeSMinfo.Periode().apply {
                        arbufoerFOM = datatypeFactory.newXMLGregorianCalendar(GregorianCalendar())
                    }
                })
            }

            val healthInformation = deafaultHelseOpplysningerArbeidsuforhet()
            healthInformation.aktivitet.periode.add(HelseOpplysningerArbeidsuforhet.Aktivitet.Periode().apply {
                periodeFOMDato = datatypeFactory.newXMLGregorianCalendar(GregorianCalendar())
                periodeFOMDato.year = infotrygdForespResponse.sMhistorikk.sykmelding.first().periode.arbufoerFOM.year - 1
            })

            val ruleData = RuleData(infotrygdForespResponse, healthInformation)
            val results = listOf<List<Rule<RuleData>>>(
                    ValidationRules.values().toList()
            ).flatten().filter { rule -> rule.predicate(ruleData) }

            results.any { it == ValidationRules.PARTIALLY_COINCIDENT_SICK_LEAVE_PERIOD_WITH_PREVIOUSLY_REGISTERED_SICK_LEAVE } shouldEqual true
        }

        it("Should check rule 1515") {
            val healthInformation = deafaultHelseOpplysningerArbeidsuforhet()
            healthInformation.aktivitet.periode.add(HelseOpplysningerArbeidsuforhet.Aktivitet.Periode().apply {
                periodeFOMDato = datatypeFactory.newXMLGregorianCalendar(GregorianCalendar(2018, 0, 2))
            })

            val infotrygdForespResponse = deafaultInfotrygdForesp()
            infotrygdForespResponse.sMhistorikk = InfotrygdForesp.SMhistorikk().apply {
                    sykmelding.add(TypeSMinfo().apply {
                        periode = TypeSMinfo.Periode().apply {
                            arbufoerFOM = datatypeFactory.newXMLGregorianCalendar(GregorianCalendar(2018, 0, 1))
                            friskKode = "J"
                            hovedDiagnosekode = "001"
                            utbetTOM = datatypeFactory.newXMLGregorianCalendar(GregorianCalendar(2018, 0, 3))
                        }
                    })
            }

            val ruleData = RuleData(infotrygdForespResponse, healthInformation)
            val results = listOf<List<Rule<RuleData>>>(
                    ValidationRules.values().toList()
            ).flatten().filter { rule -> rule.predicate(ruleData) }

            results.any { it == ValidationRules.SICKLEAVE_EXTENTION_FROM_DIFFRENT_NAV_OFFICE_1 } shouldEqual true
        }

        it("Should check rule 1515") {
            val healthInformation = deafaultHelseOpplysningerArbeidsuforhet()
            healthInformation.aktivitet.periode.add(HelseOpplysningerArbeidsuforhet.Aktivitet.Periode().apply {
                periodeFOMDato = datatypeFactory.newXMLGregorianCalendar(GregorianCalendar(2018, 0, 4))
            })

            val infotrygdForespResponse = deafaultInfotrygdForesp()
            infotrygdForespResponse.sMhistorikk = InfotrygdForesp.SMhistorikk().apply {
                sykmelding.add(TypeSMinfo().apply {
                    periode = TypeSMinfo.Periode().apply {
                        friskKode = "J"
                        hovedDiagnosekode = "001"
                        utbetTOM = datatypeFactory.newXMLGregorianCalendar(GregorianCalendar(2018, 0, 3))
                    }
                })
            }

            val ruleData = RuleData(infotrygdForespResponse, healthInformation)
            val results = listOf<List<Rule<RuleData>>>(
                    ValidationRules.values().toList()
            ).flatten().filter { rule -> rule.predicate(ruleData) }

            results.any { it == ValidationRules.SICKLEAVE_EXTENTION_FROM_DIFFRENT_NAV_OFFICE_2 } shouldEqual true
        }

        it("Should check rule 1515") {
            val healthInformation = deafaultHelseOpplysningerArbeidsuforhet()
            healthInformation.aktivitet.periode.add(HelseOpplysningerArbeidsuforhet.Aktivitet.Periode().apply {
                periodeFOMDato = datatypeFactory.newXMLGregorianCalendar(GregorianCalendar(2018, 10, 11))
            })

            val infotrygdForespResponse = deafaultInfotrygdForesp()
            infotrygdForespResponse.sMhistorikk = InfotrygdForesp.SMhistorikk().apply {
                sykmelding.add(TypeSMinfo().apply {
                    periode = TypeSMinfo.Periode().apply {
                        friskKode = "J"
                        hovedDiagnosekode = "001"
                        utbetTOM = datatypeFactory.newXMLGregorianCalendar(GregorianCalendar(2018, 10, 9))
                    }
                })
            }

            val ruleData = RuleData(infotrygdForespResponse, healthInformation)
            val results = listOf<List<Rule<RuleData>>>(
                    ValidationRules.values().toList()
            ).flatten().filter { rule -> rule.predicate(ruleData) }

            results.any { it == ValidationRules.SICKLEAVE_EXTENTION_FROM_DIFFRENT_NAV_OFFICE_3 } shouldEqual true
        }

        it("Should check rule 1516") {
            val healthInformation = deafaultHelseOpplysningerArbeidsuforhet()

            val infotrygdForespResponse = deafaultInfotrygdForesp()
            infotrygdForespResponse.sMhistorikk = InfotrygdForesp.SMhistorikk().apply {
                sykmelding.add(TypeSMinfo().apply {
                    periode = TypeSMinfo.Periode().apply {
                        friskmeldtDato = datatypeFactory.newXMLGregorianCalendar(GregorianCalendar(2017, 0, 1))
                        arbufoerTOM = datatypeFactory.newXMLGregorianCalendar(GregorianCalendar(2018, 0, 1))
                    }
                })
            }

            val ruleData = RuleData(infotrygdForespResponse, healthInformation)
            val results = listOf<List<Rule<RuleData>>>(
                    ValidationRules.values().toList()
            ).flatten().filter { rule -> rule.predicate(ruleData) }

            results.any { it == ValidationRules.NEW_CLEAN_BILL_DATE_BEFORE_ARBUFORTOM } shouldEqual true
        }

        it("Should check rule 1517") {
            val healthInformation = deafaultHelseOpplysningerArbeidsuforhet()

            val infotrygdForespResponse = deafaultInfotrygdForesp()
            infotrygdForespResponse.sMhistorikk = InfotrygdForesp.SMhistorikk().apply {
                sykmelding.add(TypeSMinfo().apply {
                    periode = TypeSMinfo.Periode().apply {
                        friskmeldtDato = datatypeFactory.newXMLGregorianCalendar(GregorianCalendar(2017, 0, 1))
                        utbetTOM = datatypeFactory.newXMLGregorianCalendar(GregorianCalendar(2018, 0, 1))
                    }
                })
            }

            val ruleData = RuleData(infotrygdForespResponse, healthInformation)
            val results = listOf<List<Rule<RuleData>>>(
                    ValidationRules.values().toList()
            ).flatten().filter { rule -> rule.predicate(ruleData) }

            results.any { it == ValidationRules.NEW_CLEAN_BILL_DATE_BEFORE_PAYOUT } shouldEqual true
        }

        it("Should check rule 1518") {
            val healthInformation = deafaultHelseOpplysningerArbeidsuforhet()

            val infotrygdForespResponse = deafaultInfotrygdForesp()
            infotrygdForespResponse.sMhistorikk = InfotrygdForesp.SMhistorikk().apply {
                sykmelding.add(TypeSMinfo().apply {
                    periode = TypeSMinfo.Periode().apply {
                        friskmeldtDato = datatypeFactory.newXMLGregorianCalendar(GregorianCalendar(2018, 0, 1))
                    }
                })
                sykmelding.add(TypeSMinfo().apply {
                    periode = TypeSMinfo.Periode().apply {
                        friskmeldtDato = datatypeFactory.newXMLGregorianCalendar(GregorianCalendar(2017, 0, 1))
                    }
                })
            }

            val ruleData = RuleData(infotrygdForespResponse, healthInformation)
            val results = listOf<List<Rule<RuleData>>>(
                    ValidationRules.values().toList()
            ).flatten().filter { rule -> rule.predicate(ruleData) }

                results.any { it == ValidationRules.NEW_CLEAN_BILL_DATE_BEFORE_REGISTERD_CLEAN_BILL_DATE } shouldEqual true
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
            infotrygdForespResponse.sMhistorikk = InfotrygdForesp.SMhistorikk().apply {
                sykmelding.add(TypeSMinfo().apply {
                    periode = TypeSMinfo.Periode().apply {
                        ufoeregrad = "90".toBigInteger()
                        arbufoerFOM = datatypeFactory.newXMLGregorianCalendar(GregorianCalendar(2018, 0, 1))
                    }
                })
            }

            val ruleData = RuleData(infotrygdForespResponse, healthInformation)
            val results = listOf<List<Rule<RuleData>>>(
                    ValidationRules.values().toList()
            ).flatten().filter { rule -> rule.predicate(ruleData) }

            results.any { it == ValidationRules.DIABILITY_GRADE_CANGED } shouldEqual true
        }

        it("Should check rule 1544") {
            val healthInformation = deafaultHelseOpplysningerArbeidsuforhet()
            healthInformation.aktivitet.periode.add(HelseOpplysningerArbeidsuforhet.Aktivitet.Periode().apply {
                periodeFOMDato = datatypeFactory.newXMLGregorianCalendar(GregorianCalendar(2018, 0, 1))
            })

            val infotrygdForespResponse = deafaultInfotrygdForesp()
            infotrygdForespResponse.sMhistorikk = InfotrygdForesp.SMhistorikk().apply {
                    sykmelding.add(TypeSMinfo().apply {
                    historikk.add(TypeSMinfo.Historikk().apply {
                        tilltak = TypeSMinfo.Historikk.Tilltak().apply {
                            type = "FA"
                            tom = datatypeFactory.newXMLGregorianCalendar(GregorianCalendar(2017, 0, 1))
                        }
                    })
                })
            }

            val ruleData = RuleData(infotrygdForespResponse, healthInformation)
            val results = listOf<List<Rule<RuleData>>>(
                    ValidationRules.values().toList()
            ).flatten().filter { rule -> rule.predicate(ruleData) }

            results.any { it == ValidationRules.EXTANION_OVER_FA } shouldEqual true
        }

        it("Should check rule 1545") {
            val healthInformation = deafaultHelseOpplysningerArbeidsuforhet()

            val infotrygdForespResponse = deafaultInfotrygdForesp()
            infotrygdForespResponse.sMhistorikk = InfotrygdForesp.SMhistorikk().apply {
                sykmelding.add(TypeSMinfo().apply {
                    periode = TypeSMinfo.Periode().apply {
                        stans = "DØD"
                    }
                })
            }

            val ruleData = RuleData(infotrygdForespResponse, healthInformation)
            val results = listOf<List<Rule<RuleData>>>(
                    ValidationRules.values().toList()
            ).flatten().filter { rule -> rule.predicate(ruleData) }

            results.any { it == ValidationRules.PATIENT_DEAD } shouldEqual true
        }

        it("Should check rule 1546") {
            val healthInformation = deafaultHelseOpplysningerArbeidsuforhet()

            val infotrygdForespResponse = deafaultInfotrygdForesp()
            infotrygdForespResponse.sMhistorikk = InfotrygdForesp.SMhistorikk().apply {
                sykmelding.add(TypeSMinfo().apply {
                    periode = TypeSMinfo.Periode().apply {
                        stans = "FL"
                    }
                })
            }

            val ruleData = RuleData(infotrygdForespResponse, healthInformation)
            val results = listOf<List<Rule<RuleData>>>(
                    ValidationRules.values().toList()
            ).flatten().filter { rule -> rule.predicate(ruleData) }

            results.any { it == ValidationRules.PERSON_MOVING_KODE_FL } shouldEqual true
        }

        it("Should check rule 1547") {
            val healthInformation = deafaultHelseOpplysningerArbeidsuforhet()

            val infotrygdForespResponse = deafaultInfotrygdForesp()
            infotrygdForespResponse.sMhistorikk = InfotrygdForesp.SMhistorikk().apply {
                sykmelding.add(TypeSMinfo().apply {
                    periode = TypeSMinfo.Periode().apply {
                        stans = "RT"
                    }
                })
            }

            val ruleData = RuleData(infotrygdForespResponse, healthInformation)
            val results = listOf<List<Rule<RuleData>>>(
                    ValidationRules.values().toList()
            ).flatten().filter { rule -> rule.predicate(ruleData) }

            results.any { it == ValidationRules.CASE_STOP_KODE_RT } shouldEqual true
        }

        it("Should check rule 1548") {
            val healthInformation = deafaultHelseOpplysningerArbeidsuforhet()

            val infotrygdForespResponse = deafaultInfotrygdForesp()
            infotrygdForespResponse.sMhistorikk = InfotrygdForesp.SMhistorikk().apply {
                sykmelding.add(TypeSMinfo().apply {
                    periode = TypeSMinfo.Periode().apply {
                        stans = "AD"
                    }
                })
            }

            val ruleData = RuleData(infotrygdForespResponse, healthInformation)
            val results = listOf<List<Rule<RuleData>>>(
                    ValidationRules.values().toList()
            ).flatten().filter { rule -> rule.predicate(ruleData) }

            results.any { it == ValidationRules.PERIOD_ENDED_DEAD } shouldEqual true
        }

        it("Should check rule 1549") {
            val healthInformation = deafaultHelseOpplysningerArbeidsuforhet()

            val infotrygdForespResponse = deafaultInfotrygdForesp()
            infotrygdForespResponse.sMhistorikk = InfotrygdForesp.SMhistorikk().apply {
                sykmelding.add(TypeSMinfo().apply {
                    periode = TypeSMinfo.Periode().apply {
                        stans = "AA"
                    }
                })
            }

            val ruleData = RuleData(infotrygdForespResponse, healthInformation)
            val results = listOf<List<Rule<RuleData>>>(
                    ValidationRules.values().toList()
            ).flatten().filter { rule -> rule.predicate(ruleData) }

            results.any { it == ValidationRules.PERIOD_FOR_AA_ENDED } shouldEqual true
        }

        it("Should check rule 1550") {
            val healthInformation = deafaultHelseOpplysningerArbeidsuforhet()

            val infotrygdForespResponse = deafaultInfotrygdForesp()
            infotrygdForespResponse.sMhistorikk = InfotrygdForesp.SMhistorikk().apply {
                sykmelding.add(TypeSMinfo().apply {
                    periode = TypeSMinfo.Periode().apply {
                        stans = "AF"
                    }
                })
            }

            val ruleData = RuleData(infotrygdForespResponse, healthInformation)
            val results = listOf<List<Rule<RuleData>>>(
                    ValidationRules.values().toList()
            ).flatten().filter { rule -> rule.predicate(ruleData) }

            results.any { it == ValidationRules.PERIOD_IS_AF } shouldEqual true
        }

        it("Should check rule 1551") {
            val healthInformation = deafaultHelseOpplysningerArbeidsuforhet()

            val infotrygdForespResponse = deafaultInfotrygdForesp()
            infotrygdForespResponse.sMhistorikk = InfotrygdForesp.SMhistorikk().apply {
                sykmelding.add(TypeSMinfo().apply {
                    periode = TypeSMinfo.Periode().apply {
                        stans = "MAX"
                    }
                })
            }

            val ruleData = RuleData(infotrygdForespResponse, healthInformation)
            val results = listOf<List<Rule<RuleData>>>(
                    ValidationRules.values().toList()
            ).flatten().filter { rule -> rule.predicate(ruleData) }

            results.any { it == ValidationRules.MAX_SICK_LEAVE_PAYOUT } shouldEqual true
        }

        it("Should check rule 1552") {
            val healthInformation = deafaultHelseOpplysningerArbeidsuforhet()

            val infotrygdForespResponse = deafaultInfotrygdForesp()
            infotrygdForespResponse.sMhistorikk = InfotrygdForesp.SMhistorikk().apply {
                sykmelding.add(TypeSMinfo().apply {
                    periode = TypeSMinfo.Periode().apply {
                        avslag = "Fordi"
                    }
                })
            }

            val ruleData = RuleData(infotrygdForespResponse, healthInformation)
            val results = listOf<List<Rule<RuleData>>>(
                    ValidationRules.values().toList()
            ).flatten().filter { rule -> rule.predicate(ruleData) }

            results.any { it == ValidationRules.REFUSAL_IS_REGISTERED } shouldEqual true
        }

        it("Should check rule 1591") {
            val healthInformation = deafaultHelseOpplysningerArbeidsuforhet()

            val infotrygdForespResponse = deafaultInfotrygdForesp()
            infotrygdForespResponse.hovedStatus = StatusType().apply {
                kodeMelding = "5"
            }

            val ruleData = RuleData(infotrygdForespResponse, healthInformation)
            val results = listOf<List<Rule<RuleData>>>(
                    ValidationRules.values().toList()
            ).flatten().filter { rule -> rule.predicate(ruleData) }

            results.any { it == ValidationRules.ERROR_FROM_IT_HOUVED_STATUS_KODEMELDING } shouldEqual true
        }

        it("Should check rule 1591") {
            val healthInformation = deafaultHelseOpplysningerArbeidsuforhet()
            val infotrygdForespResponse = deafaultInfotrygdForesp()

            infotrygdForespResponse.sMhistorikk = InfotrygdForesp.SMhistorikk().apply {
                status = StatusType().apply {
                    kodeMelding = "5"
                }
            }

            val ruleData = RuleData(infotrygdForespResponse, healthInformation)
            val results = listOf<List<Rule<RuleData>>>(
                    ValidationRules.values().toList()
            ).flatten().filter { rule -> rule.predicate(ruleData) }

            results.any { it == ValidationRules.ERROR_FROM_IT_SMHISTORIKK_STATUS_KODEMELDING } shouldEqual true
        }

        it("Should check rule 1591") {
            val healthInformation = deafaultHelseOpplysningerArbeidsuforhet()

            val infotrygdForespResponse = deafaultInfotrygdForesp()
            infotrygdForespResponse.parallelleYtelser = InfotrygdForesp.ParallelleYtelser().apply {
                status = StatusType().apply {
                    kodeMelding = "5"
                }
            }

            val ruleData = RuleData(infotrygdForespResponse, healthInformation)
            val results = listOf<List<Rule<RuleData>>>(
                    ValidationRules.values().toList()
            ).flatten().filter { rule -> rule.predicate(ruleData) }

            results.any { it == ValidationRules.ERROR_FROM_IT_PARALELLYTELSER_STATUS_KODEMELDING } shouldEqual true
        }

        it("Should check rule 1591") {
            val healthInformation = deafaultHelseOpplysningerArbeidsuforhet()

            val infotrygdForespResponse = deafaultInfotrygdForesp()
            infotrygdForespResponse.diagnosekodeOK = InfotrygdForesp.DiagnosekodeOK().apply {
                status = StatusType().apply {
                    kodeMelding = "5"
                }
            }

            val ruleData = RuleData(infotrygdForespResponse, healthInformation)
            val results = listOf<List<Rule<RuleData>>>(
                    ValidationRules.values().toList()
            ).flatten().filter { rule -> rule.predicate(ruleData) }

            results.any { it == ValidationRules.ERROR_FROM_IT_DIAGNOSE_OK_UTREKK_STATUS_KODEMELDING } shouldEqual true
        }

        it("Should check rule 1591") {
            val healthInformation = deafaultHelseOpplysningerArbeidsuforhet()

            val infotrygdForespResponse = deafaultInfotrygdForesp()
            infotrygdForespResponse.pasient = InfotrygdForesp.Pasient().apply {
                status = StatusType().apply {
                    kodeMelding = "5"
                }
            }

                val ruleData = RuleData(infotrygdForespResponse, healthInformation)
                val results = listOf<List<Rule<RuleData>>>(
                        ValidationRules.values().toList()
                ).flatten().filter { rule -> rule.predicate(ruleData) }

                results.any { it == ValidationRules.ERROR_FROM_IT_PASIENT_UTREKK_STATUS_KODEMELDING } shouldEqual true
            }
    }
})
