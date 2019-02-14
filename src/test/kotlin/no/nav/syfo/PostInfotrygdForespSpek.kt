package no.nav.syfo

import no.nav.helse.infotrygd.foresp.InfotrygdForesp
import no.nav.helse.infotrygd.foresp.StatusType
import no.nav.helse.infotrygd.foresp.TypeSMinfo
import no.nav.helse.sm2013.HelseOpplysningerArbeidsuforhet
import no.nav.syfo.rules.RuleData
import no.nav.syfo.rules.ValidationRuleChain

import org.amshove.kluent.shouldEqual
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.LocalDate

object PostInfotrygdForespSpek : Spek({
    fun deafaultHelseOpplysningerArbeidsuforhet() = HelseOpplysningerArbeidsuforhet().apply {
        aktivitet = HelseOpplysningerArbeidsuforhet.Aktivitet().apply {
        }
    }

    fun deafaultInfotrygdForesp() = InfotrygdForesp().apply {
        hovedStatus = StatusType().apply {
            kodeMelding = "00"
        }
    }

    fun ruleData(
        infotrygdForesp: InfotrygdForesp,
        healthInformation: HelseOpplysningerArbeidsuforhet
    ): RuleData = RuleData(infotrygdForesp, healthInformation)

    describe("Testing infotrygd rules and checking the rule outcomes") {

        it("Should check rule 1250") {
            val healthInformation = deafaultHelseOpplysningerArbeidsuforhet()
            healthInformation.aktivitet.periode.add(HelseOpplysningerArbeidsuforhet.Aktivitet.Periode().apply {
                gradertSykmelding = HelseOpplysningerArbeidsuforhet.Aktivitet.Periode.GradertSykmelding().apply {
                    sykmeldingsgrad = 90
                    isReisetilskudd = true
                }
                isReisetilskudd = true
            })

            val infotrygdForespResponse = deafaultInfotrygdForesp()

            ValidationRuleChain.GRADUAL_SYKMELDING_COMBINED_WITH_TRAVEL(ruleData(infotrygdForespResponse, healthInformation)) shouldEqual true
        }

        it("Should check rule 1260") {
            val healthInformation = deafaultHelseOpplysningerArbeidsuforhet()
            healthInformation.aktivitet.periode.add(HelseOpplysningerArbeidsuforhet.Aktivitet.Periode().apply {
                behandlingsdager = HelseOpplysningerArbeidsuforhet.Aktivitet.Periode.Behandlingsdager().apply {
                    antallBehandlingsdagerUke = 1
                }
            })

            val infotrygdForespResponse = deafaultInfotrygdForesp()

            ValidationRuleChain.NUMBER_OF_TREATMENT_DAYS_SET(ruleData(infotrygdForespResponse, healthInformation)) shouldEqual true
        }

        it("Should check rule 1270") {
            val healthInformation = deafaultHelseOpplysningerArbeidsuforhet()
            healthInformation.aktivitet.periode.add(HelseOpplysningerArbeidsuforhet.Aktivitet.Periode().apply {
                isReisetilskudd = true
            })

            val infotrygdForespResponse = deafaultInfotrygdForesp()

            ValidationRuleChain.TRAVEL_SUBSIDY_SPECIFIED(ruleData(infotrygdForespResponse, healthInformation)) shouldEqual true
        }

        it("Should check rule 1501") {
            val healthInformation = deafaultHelseOpplysningerArbeidsuforhet()
            val infotrygdForespResponse = deafaultInfotrygdForesp()
            infotrygdForespResponse.pasient = InfotrygdForesp.Pasient().apply {
                isFinnes = false
            }

            ValidationRuleChain.PATIENT_NOT_IN_IP(ruleData(infotrygdForespResponse, healthInformation)) shouldEqual true
            }

        it("Should check rule 1513") {

            val infotrygdForespResponse = deafaultInfotrygdForesp()
            infotrygdForespResponse.sMhistorikk = InfotrygdForesp.SMhistorikk().apply {
                sykmelding.add(TypeSMinfo().apply {
                    periode = TypeSMinfo.Periode().apply {
                        arbufoerFOM = LocalDate.of(2018, 1, 2)
                        arbufoerTOM = LocalDate.of(2018, 1, 8)
                    }
                })
            }

            val healthInformation = deafaultHelseOpplysningerArbeidsuforhet()
            healthInformation.aktivitet.periode.add(HelseOpplysningerArbeidsuforhet.Aktivitet.Periode().apply {
                periodeFOMDato = LocalDate.of(2018, 1, 7)
                periodeTOMDato = LocalDate.of(2018, 1, 9)
            })

            ValidationRuleChain.PARTIALLY_COINCIDENT_SICK_LEAVE_PERIOD_WITH_PREVIOUSLY_REGISTERED_SICK_LEAVE(ruleData(infotrygdForespResponse, healthInformation)) shouldEqual true
        }

        it("Should check rule 1515") {
            val healthInformation = deafaultHelseOpplysningerArbeidsuforhet()
            healthInformation.aktivitet.periode.add(HelseOpplysningerArbeidsuforhet.Aktivitet.Periode().apply {
                periodeFOMDato = LocalDate.of(2018, 1, 2)
            })

            val infotrygdForespResponse = deafaultInfotrygdForesp()
            infotrygdForespResponse.sMhistorikk = InfotrygdForesp.SMhistorikk().apply {
                    sykmelding.add(TypeSMinfo().apply {
                        periode = TypeSMinfo.Periode().apply {
                            arbufoerFOM = LocalDate.of(2018, 1, 1)
                            friskKode = "J"
                            hovedDiagnosekode = "001"
                            utbetTOM = LocalDate.of(2018, 1, 3)
                        }
                    })
            }

            ValidationRuleChain.SICKLEAVE_EXTENTION_FROM_DIFFRENT_NAV_OFFICE_1(ruleData(infotrygdForespResponse, healthInformation)) shouldEqual true
        }

        it("Should check rule 1515") {
            val healthInformation = deafaultHelseOpplysningerArbeidsuforhet()
            healthInformation.aktivitet.periode.add(HelseOpplysningerArbeidsuforhet.Aktivitet.Periode().apply {
                periodeFOMDato = LocalDate.of(2018, 1, 4)
            })

            val infotrygdForespResponse = deafaultInfotrygdForesp()
            infotrygdForespResponse.sMhistorikk = InfotrygdForesp.SMhistorikk().apply {
                sykmelding.add(TypeSMinfo().apply {
                    periode = TypeSMinfo.Periode().apply {
                        friskKode = "J"
                        hovedDiagnosekode = "001"
                        utbetTOM = LocalDate.of(2018, 1, 3)
                    }
                })
            }

            ValidationRuleChain.SICKLEAVE_EXTENTION_FROM_DIFFRENT_NAV_OFFICE_2(ruleData(infotrygdForespResponse, healthInformation)) shouldEqual true
        }

        it("Should check rule 1515") {
            val healthInformation = deafaultHelseOpplysningerArbeidsuforhet()
            healthInformation.aktivitet.periode.add(HelseOpplysningerArbeidsuforhet.Aktivitet.Periode().apply {
                periodeFOMDato = LocalDate.of(2018, 1, 6)
            })

            val infotrygdForespResponse = deafaultInfotrygdForesp()
            infotrygdForespResponse.sMhistorikk = InfotrygdForesp.SMhistorikk().apply {
                sykmelding.add(TypeSMinfo().apply {
                    periode = TypeSMinfo.Periode().apply {
                        friskKode = "J"
                        hovedDiagnosekode = "001"
                        utbetTOM = LocalDate.of(2018, 1, 5)
                    }
                })
            }

            ValidationRuleChain.SICKLEAVE_EXTENTION_FROM_DIFFRENT_NAV_OFFICE_3(ruleData(infotrygdForespResponse, healthInformation)) shouldEqual true
        }

        it("Should check rule 1516") {
            val healthInformation = deafaultHelseOpplysningerArbeidsuforhet()
            healthInformation.aktivitet.periode.add(
                    HelseOpplysningerArbeidsuforhet.Aktivitet.Periode().apply {
                        periodeTOMDato = LocalDate.of(2017, 1, 2)
                    }
            )
            healthInformation.prognose = HelseOpplysningerArbeidsuforhet.Prognose().apply {
                isArbeidsforEtterEndtPeriode = true
            }

            val infotrygdForespResponse = deafaultInfotrygdForesp()
            infotrygdForespResponse.sMhistorikk = InfotrygdForesp.SMhistorikk().apply {
                sykmelding.add(TypeSMinfo().apply {
                    periode = TypeSMinfo.Periode().apply {
                        friskmeldtDato = LocalDate.of(2017, 1, 1)
                        arbufoerTOM = LocalDate.of(2018, 1, 1)
                    }
                })
            }

            ValidationRuleChain.NEW_CLEAN_BILL_DATE_BEFORE_ARBUFORTOM(ruleData(infotrygdForespResponse, healthInformation)) shouldEqual true
        }

        it("Should check rule 1517") {
            val healthInformation = deafaultHelseOpplysningerArbeidsuforhet()
            healthInformation.aktivitet.periode.add(
                    HelseOpplysningerArbeidsuforhet.Aktivitet.Periode().apply {
                        periodeTOMDato = LocalDate.of(2017, 1, 2)
                    }
            )
            healthInformation.prognose = HelseOpplysningerArbeidsuforhet.Prognose().apply {
                isArbeidsforEtterEndtPeriode = true
            }

            val infotrygdForespResponse = deafaultInfotrygdForesp()
            infotrygdForespResponse.sMhistorikk = InfotrygdForesp.SMhistorikk().apply {
                sykmelding.add(TypeSMinfo().apply {
                    periode = TypeSMinfo.Periode().apply {
                        friskmeldtDato = LocalDate.of(2017, 1, 1)
                        utbetTOM = LocalDate.of(2018, 1, 1)
                    }
                })
            }

            ValidationRuleChain.NEW_CLEAN_BILL_DATE_BEFORE_PAYOUT(ruleData(infotrygdForespResponse, healthInformation)) shouldEqual true
        }

        it("Should check rule 1518") {
            val healthInformation = deafaultHelseOpplysningerArbeidsuforhet()
            healthInformation.aktivitet.periode.add(
                    HelseOpplysningerArbeidsuforhet.Aktivitet.Periode().apply {
                        periodeTOMDato = LocalDate.of(2017, 1, 2)
                    }
            )
            healthInformation.prognose = HelseOpplysningerArbeidsuforhet.Prognose().apply {
                isArbeidsforEtterEndtPeriode = true
            }

            val infotrygdForespResponse = deafaultInfotrygdForesp()
            infotrygdForespResponse.sMhistorikk = InfotrygdForesp.SMhistorikk().apply {
                sykmelding.add(TypeSMinfo().apply {
                    periode = TypeSMinfo.Periode().apply {
                        friskmeldtDato = LocalDate.of(2018, 1, 1)
                    }
                })
                sykmelding.add(TypeSMinfo().apply {
                    periode = TypeSMinfo.Periode().apply {
                        friskmeldtDato = LocalDate.of(2017, 1, 1)
                    }
                })
            }
            ValidationRuleChain.NEW_CLEAN_BILL_DATE_BEFORE_REGISTERD_CLEAN_BILL_DATE(ruleData(infotrygdForespResponse, healthInformation)) shouldEqual true
            }

        it("Should check rule 1544") {
            val healthInformation = deafaultHelseOpplysningerArbeidsuforhet()
            healthInformation.aktivitet.periode.add(HelseOpplysningerArbeidsuforhet.Aktivitet.Periode().apply {
                periodeFOMDato = LocalDate.of(2018, 1, 1)
            })

            val infotrygdForespResponse = deafaultInfotrygdForesp()
            infotrygdForespResponse.sMhistorikk = InfotrygdForesp.SMhistorikk().apply {
                    sykmelding.add(TypeSMinfo().apply {
                    historikk.add(TypeSMinfo.Historikk().apply {
                        tilltak = TypeSMinfo.Historikk.Tilltak().apply {
                            type = "FA"
                            tom = LocalDate.of(2017, 1, 1)
                        }
                    })
                })
            }
            ValidationRuleChain.EXTANION_OVER_FA(ruleData(infotrygdForespResponse, healthInformation)) shouldEqual true
        }

        it("Should check rule 1546") {
            val healthInformation = deafaultHelseOpplysningerArbeidsuforhet()

            val infotrygdForespResponse = deafaultInfotrygdForesp()
            infotrygdForespResponse.sMhistorikk = InfotrygdForesp.SMhistorikk().apply {
                sykmelding.add(TypeSMinfo().apply {
                    periode = TypeSMinfo.Periode().apply {
                        stans = "FL"
                        arbufoerFOM = LocalDate.of(2017, 2, 1)
                    }
                })
                sykmelding.add(TypeSMinfo().apply {
                    periode = TypeSMinfo.Periode().apply {
                        arbufoerFOM = LocalDate.of(2017, 1, 1)
                    }
                })
            }

            ValidationRuleChain.PERSON_MOVING_KODE_FL(ruleData(infotrygdForespResponse, healthInformation)) shouldEqual true
        }

        it("Should check rule 1549") {
            val healthInformation = deafaultHelseOpplysningerArbeidsuforhet()

            val infotrygdForespResponse = deafaultInfotrygdForesp()
            infotrygdForespResponse.sMhistorikk = InfotrygdForesp.SMhistorikk().apply {
                sykmelding.add(TypeSMinfo().apply {
                    periode = TypeSMinfo.Periode().apply {
                        stans = "AA"
                        arbufoerFOM = LocalDate.of(2017, 2, 1)
                    }
                })

                sykmelding.add(TypeSMinfo().apply {
                    periode = TypeSMinfo.Periode().apply {
                        arbufoerFOM = LocalDate.of(2017, 1, 1)
                    }
                })
            }

            ValidationRuleChain.PERIOD_FOR_AA_ENDED(ruleData(infotrygdForespResponse, healthInformation)) shouldEqual true
        }

        it("Should check rule 1550") {
            val healthInformation = deafaultHelseOpplysningerArbeidsuforhet()

            val infotrygdForespResponse = deafaultInfotrygdForesp()
            infotrygdForespResponse.sMhistorikk = InfotrygdForesp.SMhistorikk().apply {
                sykmelding.add(TypeSMinfo().apply {
                    periode = TypeSMinfo.Periode().apply {
                        stans = "AF"
                        arbufoerFOM = LocalDate.of(2017, 2, 1)
                    }
                })

                sykmelding.add(TypeSMinfo().apply {
                    periode = TypeSMinfo.Periode().apply {
                        arbufoerFOM = LocalDate.of(2017, 1, 1)
                    }
                })
            }

            ValidationRuleChain.PERIOD_IS_AF(ruleData(infotrygdForespResponse, healthInformation)) shouldEqual true
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

            ValidationRuleChain.MAX_SICK_LEAVE_PAYOUT(ruleData(infotrygdForespResponse, healthInformation)) shouldEqual true
        }

        it("Should check rule 1591") {
            val healthInformation = deafaultHelseOpplysningerArbeidsuforhet()

            val infotrygdForespResponse = deafaultInfotrygdForesp()
            infotrygdForespResponse.hovedStatus = StatusType().apply {
                kodeMelding = "5"
            }

            ValidationRuleChain.ERROR_FROM_IT_HOUVED_STATUS_KODEMELDING(ruleData(infotrygdForespResponse, healthInformation)) shouldEqual true
        }

        it("Should check rule 1591") {
            val healthInformation = deafaultHelseOpplysningerArbeidsuforhet()
            val infotrygdForespResponse = deafaultInfotrygdForesp()

            infotrygdForespResponse.sMhistorikk = InfotrygdForesp.SMhistorikk().apply {
                status = StatusType().apply {
                    kodeMelding = "5"
                }
            }

            ValidationRuleChain.ERROR_FROM_IT_SMHISTORIKK_STATUS_KODEMELDING(ruleData(infotrygdForespResponse, healthInformation)) shouldEqual true
        }

        it("Should check rule 1591") {
            val healthInformation = deafaultHelseOpplysningerArbeidsuforhet()

            val infotrygdForespResponse = deafaultInfotrygdForesp()
            infotrygdForespResponse.parallelleYtelser = InfotrygdForesp.ParallelleYtelser().apply {
                status = StatusType().apply {
                    kodeMelding = "5"
                }
            }

            ValidationRuleChain.ERROR_FROM_IT_PARALELLYTELSER_STATUS_KODEMELDING(ruleData(infotrygdForespResponse, healthInformation)) shouldEqual true
        }

        it("Should check rule 1591") {
            val healthInformation = deafaultHelseOpplysningerArbeidsuforhet()

            val infotrygdForespResponse = deafaultInfotrygdForesp()
            infotrygdForespResponse.diagnosekodeOK = InfotrygdForesp.DiagnosekodeOK().apply {
                status = StatusType().apply {
                    kodeMelding = "5"
                }
            }

            ValidationRuleChain.ERROR_FROM_IT_DIAGNOSE_OK_UTREKK_STATUS_KODEMELDING(ruleData(infotrygdForespResponse, healthInformation)) shouldEqual true
        }

        it("Should check rule 1591") {
            val healthInformation = deafaultHelseOpplysningerArbeidsuforhet()

            val infotrygdForespResponse = deafaultInfotrygdForesp()
            infotrygdForespResponse.pasient = InfotrygdForesp.Pasient().apply {
                status = StatusType().apply {
                    kodeMelding = "5"
                }
            }

                ValidationRuleChain.ERROR_FROM_IT_PASIENT_UTREKK_STATUS_KODEMELDING(ruleData(infotrygdForespResponse, healthInformation)) shouldEqual true
            }
    }
})
