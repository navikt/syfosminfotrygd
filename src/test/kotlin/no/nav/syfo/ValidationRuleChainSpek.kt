package no.nav.syfo

import no.nav.helse.infotrygd.foresp.InfotrygdForesp
import no.nav.helse.infotrygd.foresp.StatusType
import no.nav.helse.infotrygd.foresp.TypeSMinfo
import no.nav.helse.sm2013.HelseOpplysningerArbeidsuforhet
import no.nav.syfo.rules.ValidationRuleChain

import org.amshove.kluent.shouldEqual
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.LocalDate

object ValidationRuleChainSpek : Spek({
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
        healthInformation: HelseOpplysningerArbeidsuforhet,
        infotrygdForesp: InfotrygdForesp
    ): RuleData<InfotrygdForesp> = RuleData(healthInformation, infotrygdForesp)

    describe("Testing infotrygd rules and checking the rule outcomes") {

        it("Should check rule GRADUAL_SYKMELDING_COMBINED_WITH_TRAVEL, should trigger rule") {
            val healthInformation = deafaultHelseOpplysningerArbeidsuforhet()
            healthInformation.aktivitet.periode.add(HelseOpplysningerArbeidsuforhet.Aktivitet.Periode().apply {
                gradertSykmelding = HelseOpplysningerArbeidsuforhet.Aktivitet.Periode.GradertSykmelding().apply {
                    sykmeldingsgrad = 90
                    isReisetilskudd = true
                }
                isReisetilskudd = true
            })

            val infotrygdForespResponse = deafaultInfotrygdForesp()

            ValidationRuleChain.GRADUAL_SYKMELDING_COMBINED_WITH_TRAVEL(ruleData(healthInformation, infotrygdForespResponse)) shouldEqual true
        }

        it("Should check rule GRADUAL_SYKMELDING_COMBINED_WITH_TRAVEL, should NOT trigger rule") {
            val healthInformation = deafaultHelseOpplysningerArbeidsuforhet()
            healthInformation.aktivitet.periode.add(HelseOpplysningerArbeidsuforhet.Aktivitet.Periode().apply {
                gradertSykmelding = HelseOpplysningerArbeidsuforhet.Aktivitet.Periode.GradertSykmelding().apply {
                    sykmeldingsgrad = 90
                    isReisetilskudd = true
                }
                isReisetilskudd = false
            })

            val infotrygdForespResponse = deafaultInfotrygdForesp()

            ValidationRuleChain.GRADUAL_SYKMELDING_COMBINED_WITH_TRAVEL(ruleData(healthInformation, infotrygdForespResponse)) shouldEqual false
        }

        it("Should check rule NUMBER_OF_TREATMENT_DAYS_SET, should trigger rule") {
            val healthInformation = deafaultHelseOpplysningerArbeidsuforhet()
            healthInformation.aktivitet.periode.add(HelseOpplysningerArbeidsuforhet.Aktivitet.Periode().apply {
                behandlingsdager = HelseOpplysningerArbeidsuforhet.Aktivitet.Periode.Behandlingsdager().apply {
                    antallBehandlingsdagerUke = 1
                }
            })

            val infotrygdForespResponse = deafaultInfotrygdForesp()

            ValidationRuleChain.NUMBER_OF_TREATMENT_DAYS_SET(ruleData(healthInformation, infotrygdForespResponse)) shouldEqual true
        }

        it("Should check rule NUMBER_OF_TREATMENT_DAYS_SET, should NOT trigger rule") {
            val healthInformation = deafaultHelseOpplysningerArbeidsuforhet()
            healthInformation.aktivitet.periode.add(HelseOpplysningerArbeidsuforhet.Aktivitet.Periode().apply {
            })

            val infotrygdForespResponse = deafaultInfotrygdForesp()

            ValidationRuleChain.NUMBER_OF_TREATMENT_DAYS_SET(ruleData(healthInformation, infotrygdForespResponse)) shouldEqual false
        }

        it("Should check rule TRAVEL_SUBSIDY_SPECIFIED, should trigger rule") {
            val healthInformation = deafaultHelseOpplysningerArbeidsuforhet()
            healthInformation.aktivitet.periode.add(HelseOpplysningerArbeidsuforhet.Aktivitet.Periode().apply {
                isReisetilskudd = true
            })

            val infotrygdForespResponse = deafaultInfotrygdForesp()

            ValidationRuleChain.TRAVEL_SUBSIDY_SPECIFIED(ruleData(healthInformation, infotrygdForespResponse)) shouldEqual true
        }

        it("Should check rule TRAVEL_SUBSIDY_SPECIFIED, should NOT trigger rule") {
            val healthInformation = deafaultHelseOpplysningerArbeidsuforhet()
            healthInformation.aktivitet.periode.add(HelseOpplysningerArbeidsuforhet.Aktivitet.Periode().apply {
                isReisetilskudd = false
            })

            val infotrygdForespResponse = deafaultInfotrygdForesp()

            ValidationRuleChain.TRAVEL_SUBSIDY_SPECIFIED(ruleData(healthInformation, infotrygdForespResponse)) shouldEqual false
        }

        it("Should check rule PATIENT_NOT_IN_IP, should trigger rule") {
            val healthInformation = deafaultHelseOpplysningerArbeidsuforhet()
            val infotrygdForespResponse = deafaultInfotrygdForesp()
            infotrygdForespResponse.pasient = InfotrygdForesp.Pasient().apply {
                isFinnes = false
            }

            ValidationRuleChain.PATIENT_NOT_IN_IP(ruleData(healthInformation, infotrygdForespResponse)) shouldEqual true
            }

        it("Should check rule PATIENT_NOT_IN_IP, should NOT trigger rule") {
            val healthInformation = deafaultHelseOpplysningerArbeidsuforhet()
            val infotrygdForespResponse = deafaultInfotrygdForesp()
            infotrygdForespResponse.pasient = InfotrygdForesp.Pasient().apply {
                isFinnes = true
            }

            ValidationRuleChain.PATIENT_NOT_IN_IP(ruleData(healthInformation, infotrygdForespResponse)) shouldEqual false
        }

        it("Should check rule PARTIALLY_COINCIDENT_SICK_LEAVE_PERIOD_WITH_PREVIOUSLY_REGISTERED_SICK_LEAVE, should trigger rule") {

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
                periodeFOMDato = LocalDate.of(2018, 1, 1)
            })

            ValidationRuleChain.PARTIALLY_COINCIDENT_SICK_LEAVE_PERIOD_WITH_PREVIOUSLY_REGISTERED_SICK_LEAVE(ruleData(healthInformation, infotrygdForespResponse)) shouldEqual true
        }

        it("Should check rule PARTIALLY_COINCIDENT_SICK_LEAVE_PERIOD_WITH_PREVIOUSLY_REGISTERED_SICK_LEAVE, should NOT trigger rule") {

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
                periodeFOMDato = LocalDate.of(2018, 1, 9)
                periodeTOMDato = LocalDate.of(2018, 1, 15)
            })

            ValidationRuleChain.PARTIALLY_COINCIDENT_SICK_LEAVE_PERIOD_WITH_PREVIOUSLY_REGISTERED_SICK_LEAVE(ruleData(healthInformation, infotrygdForespResponse)) shouldEqual false
        }

        it("Should check rule SICKLEAVE_EXTENTION_FROM_DIFFRENT_NAV_OFFICE_1, should trigger rule") {
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

            ValidationRuleChain.SICKLEAVE_EXTENTION_FROM_DIFFRENT_NAV_OFFICE_1(ruleData(healthInformation, infotrygdForespResponse)) shouldEqual true
        }

        it("Should check rule SICKLEAVE_EXTENTION_FROM_DIFFRENT_NAV_OFFICE_1, should NOT trigger rule") {
            val healthInformation = deafaultHelseOpplysningerArbeidsuforhet()
            healthInformation.aktivitet.periode.add(HelseOpplysningerArbeidsuforhet.Aktivitet.Periode().apply {
                periodeFOMDato = LocalDate.of(2018, 1, 3)
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

            ValidationRuleChain.SICKLEAVE_EXTENTION_FROM_DIFFRENT_NAV_OFFICE_1(ruleData(healthInformation, infotrygdForespResponse)) shouldEqual false
        }

        it("Should check rule SICKLEAVE_EXTENTION_FROM_DIFFRENT_NAV_OFFICE_2, should trigger rule") {
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

            ValidationRuleChain.SICKLEAVE_EXTENTION_FROM_DIFFRENT_NAV_OFFICE_2(ruleData(healthInformation, infotrygdForespResponse)) shouldEqual true
        }

        it("Should check rule SICKLEAVE_EXTENTION_FROM_DIFFRENT_NAV_OFFICE_2, should NOT trigger rule") {
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
                        utbetTOM = LocalDate.of(2018, 1, 4)
                    }
                })
            }

            ValidationRuleChain.SICKLEAVE_EXTENTION_FROM_DIFFRENT_NAV_OFFICE_2(ruleData(healthInformation, infotrygdForespResponse)) shouldEqual false
        }

        it("Should check rule SICKLEAVE_EXTENTION_FROM_DIFFRENT_NAV_OFFICE_3, should trigger rule") {
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

            ValidationRuleChain.SICKLEAVE_EXTENTION_FROM_DIFFRENT_NAV_OFFICE_3(ruleData(healthInformation, infotrygdForespResponse)) shouldEqual true
        }

        it("Should check rule SICKLEAVE_EXTENTION_FROM_DIFFRENT_NAV_OFFICE_3, should NOT trigger rule") {
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
                        utbetTOM = LocalDate.of(2018, 1, 6)
                    }
                })
            }

            ValidationRuleChain.SICKLEAVE_EXTENTION_FROM_DIFFRENT_NAV_OFFICE_3(ruleData(healthInformation, infotrygdForespResponse)) shouldEqual false
        }

        it("Should check rule NEW_CLEAN_BILL_DATE_BEFORE_ARBUFORTOM, should trigger rule") {
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
                        arbufoerTOM = LocalDate.of(2018, 1, 1)
                    }
                })
            }

            ValidationRuleChain.NEW_CLEAN_BILL_DATE_BEFORE_ARBUFORTOM(ruleData(healthInformation, infotrygdForespResponse)) shouldEqual true
        }

        it("Should check rule NEW_CLEAN_BILL_DATE_BEFORE_ARBUFORTOM, should NOT trigger rule") {
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
                status = StatusType().apply {
                    kodeMelding = "04"
                }
            }

            ValidationRuleChain.NEW_CLEAN_BILL_DATE_BEFORE_ARBUFORTOM(ruleData(healthInformation, infotrygdForespResponse)) shouldEqual false
        }

        it("Should check rule NEW_CLEAN_BILL_DATE_BEFORE_PAYOUT, should trigger rule") {
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

            ValidationRuleChain.NEW_CLEAN_BILL_DATE_BEFORE_PAYOUT(ruleData(healthInformation, infotrygdForespResponse)) shouldEqual true
        }

        it("Should check rule NEW_CLEAN_BILL_DATE_BEFORE_PAYOUT, should NOT trigger rule") {
            val healthInformation = deafaultHelseOpplysningerArbeidsuforhet()
            healthInformation.aktivitet.periode.add(
                    HelseOpplysningerArbeidsuforhet.Aktivitet.Periode().apply {
                        periodeTOMDato = LocalDate.of(2017, 1, 3)
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
                        utbetTOM = LocalDate.of(2017, 1, 2)
                    }
                })
            }

            ValidationRuleChain.NEW_CLEAN_BILL_DATE_BEFORE_PAYOUT(ruleData(healthInformation, infotrygdForespResponse)) shouldEqual false
        }

        it("Should check rule NEW_CLEAN_BILL_DATE_BEFORE_REGISTERD_CLEAN_BILL_DATE, should trigger rule") {
            val healthInformation = deafaultHelseOpplysningerArbeidsuforhet()
            healthInformation.aktivitet.periode.add(
                    HelseOpplysningerArbeidsuforhet.Aktivitet.Periode().apply {
                        periodeTOMDato = LocalDate.of(2018, 1, 2)
                    }
            )
            healthInformation.prognose = HelseOpplysningerArbeidsuforhet.Prognose().apply {
                isArbeidsforEtterEndtPeriode = true
            }

            val infotrygdForespResponse = deafaultInfotrygdForesp()
            infotrygdForespResponse.sMhistorikk = InfotrygdForesp.SMhistorikk().apply {
                sykmelding.add(TypeSMinfo().apply {
                    periode = TypeSMinfo.Periode().apply {
                        friskmeldtDato = LocalDate.of(2018, 1, 3)
                    }
                })
            }
            ValidationRuleChain.NEW_CLEAN_BILL_DATE_BEFORE_REGISTERD_CLEAN_BILL_DATE(ruleData(healthInformation, infotrygdForespResponse)) shouldEqual true
            }

        it("Should check rule NEW_CLEAN_BILL_DATE_BEFORE_REGISTERD_CLEAN_BILL_DATE, should NOT trigger rule") {
            val healthInformation = deafaultHelseOpplysningerArbeidsuforhet()
            healthInformation.aktivitet.periode.add(
                    HelseOpplysningerArbeidsuforhet.Aktivitet.Periode().apply {
                        periodeTOMDato = LocalDate.of(2018, 1, 2)
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
            }
            ValidationRuleChain.NEW_CLEAN_BILL_DATE_BEFORE_REGISTERD_CLEAN_BILL_DATE(ruleData(healthInformation, infotrygdForespResponse)) shouldEqual false
        }

        it("Should check rule NEW_CLEAN_BILL_DATE_BEFORE_REGISTERD_CLEAN_BILL_DATE, should NOT trigger rule") {
            val healthInformation = deafaultHelseOpplysningerArbeidsuforhet()
            healthInformation.aktivitet.periode.add(
                    HelseOpplysningerArbeidsuforhet.Aktivitet.Periode().apply {
                        periodeTOMDato = LocalDate.of(2018, 1, 2)
                    }
            )
            healthInformation.prognose = HelseOpplysningerArbeidsuforhet.Prognose().apply {
                isArbeidsforEtterEndtPeriode = true
            }

            val infotrygdForespResponse = deafaultInfotrygdForesp()
            infotrygdForespResponse.sMhistorikk = InfotrygdForesp.SMhistorikk().apply {
                sykmelding.add(TypeSMinfo().apply {
                })
            }
            ValidationRuleChain.NEW_CLEAN_BILL_DATE_BEFORE_REGISTERD_CLEAN_BILL_DATE(ruleData(healthInformation, infotrygdForespResponse)) shouldEqual false
        }

        it("Should check rule EXTANION_OVER_FA, should trigger rule") {
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
            ValidationRuleChain.EXTANION_OVER_FA(ruleData(healthInformation, infotrygdForespResponse)) shouldEqual true
        }

        it("Should check rule EXTANION_OVER_FA, should NOT trigger rule") {
            val healthInformation = deafaultHelseOpplysningerArbeidsuforhet()
            healthInformation.aktivitet.periode.add(HelseOpplysningerArbeidsuforhet.Aktivitet.Periode().apply {
                periodeFOMDato = LocalDate.of(2018, 1, 1)
            })

            val infotrygdForespResponse = deafaultInfotrygdForesp()
            infotrygdForespResponse.sMhistorikk = InfotrygdForesp.SMhistorikk().apply {
                sykmelding.add(TypeSMinfo().apply {
                    historikk.add(TypeSMinfo.Historikk().apply {
                        tilltak = TypeSMinfo.Historikk.Tilltak().apply {
                            type = "LA"
                            tom = LocalDate.of(2017, 1, 1)
                        }
                    })
                })
            }
            ValidationRuleChain.EXTANION_OVER_FA(ruleData(healthInformation, infotrygdForespResponse)) shouldEqual false
        }

        it("Should check rule PERSON_MOVING_KODE_FL, should trigger rule") {
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

            ValidationRuleChain.PERSON_MOVING_KODE_FL(ruleData(healthInformation, infotrygdForespResponse)) shouldEqual true
        }

        it("Should check rule PERSON_MOVING_KODE_FL, should NOT trigger rule") {
            val healthInformation = deafaultHelseOpplysningerArbeidsuforhet()

            val infotrygdForespResponse = deafaultInfotrygdForesp()
            infotrygdForespResponse.sMhistorikk = InfotrygdForesp.SMhistorikk().apply {
                sykmelding.add(TypeSMinfo().apply {
                    periode = TypeSMinfo.Periode().apply {
                        stans = "PL"
                        arbufoerFOM = LocalDate.of(2017, 2, 1)
                    }
                })
                sykmelding.add(TypeSMinfo().apply {
                    periode = TypeSMinfo.Periode().apply {
                        arbufoerFOM = LocalDate.of(2017, 1, 1)
                    }
                })
            }

            ValidationRuleChain.PERSON_MOVING_KODE_FL(ruleData(healthInformation, infotrygdForespResponse)) shouldEqual false
        }

        it("Should check rule PERIOD_FOR_AA_ENDED, should trigger rule") {
            val healthInformation = deafaultHelseOpplysningerArbeidsuforhet()
            healthInformation.aktivitet.periode.add(HelseOpplysningerArbeidsuforhet.Aktivitet.Periode().apply {
                periodeFOMDato = LocalDate.of(2017, 2, 1)
            })

            val infotrygdForespResponse = deafaultInfotrygdForesp()
            infotrygdForespResponse.sMhistorikk = InfotrygdForesp.SMhistorikk().apply {
                sykmelding.add(TypeSMinfo().apply {
                    periode = TypeSMinfo.Periode().apply {
                        stans = "AA"
                        arbufoerTOM = LocalDate.of(2017, 2, 2)
                    }
                })
            }

            ValidationRuleChain.PERIOD_FOR_AA_ENDED(ruleData(healthInformation, infotrygdForespResponse)) shouldEqual true
        }

        it("Should check rule PERIOD_FOR_AA_ENDED, should NOT trigger rule") {
            val healthInformation = deafaultHelseOpplysningerArbeidsuforhet()
            healthInformation.aktivitet.periode.add(HelseOpplysningerArbeidsuforhet.Aktivitet.Periode().apply {
                periodeFOMDato = LocalDate.of(2017, 2, 2)
            })

            val infotrygdForespResponse = deafaultInfotrygdForesp()
            infotrygdForespResponse.sMhistorikk = InfotrygdForesp.SMhistorikk().apply {
                sykmelding.add(TypeSMinfo().apply {
                    periode = TypeSMinfo.Periode().apply {
                        stans = "AA"
                        arbufoerTOM = LocalDate.of(2017, 2, 1)
                    }
                })
            }

            ValidationRuleChain.PERIOD_FOR_AA_ENDED(ruleData(healthInformation, infotrygdForespResponse)) shouldEqual false
        }

        it("Should check rule PERIOD_IS_AF, should trigger rule") {
            val healthInformation = deafaultHelseOpplysningerArbeidsuforhet()
            healthInformation.aktivitet.periode.add(HelseOpplysningerArbeidsuforhet.Aktivitet.Periode().apply {
                periodeFOMDato = LocalDate.of(2017, 2, 1)
            })

            val infotrygdForespResponse = deafaultInfotrygdForesp()
            infotrygdForespResponse.sMhistorikk = InfotrygdForesp.SMhistorikk().apply {
                sykmelding.add(TypeSMinfo().apply {
                    periode = TypeSMinfo.Periode().apply {
                        stans = "AF"
                        arbufoerTOM = LocalDate.of(2017, 2, 2)
                    }
                })
            }

            ValidationRuleChain.PERIOD_IS_AF(ruleData(healthInformation, infotrygdForespResponse)) shouldEqual true
        }

        it("Should check rule PERIOD_IS_AF, should NOT trigger rule") {
            val healthInformation = deafaultHelseOpplysningerArbeidsuforhet()
            healthInformation.aktivitet.periode.add(HelseOpplysningerArbeidsuforhet.Aktivitet.Periode().apply {
                periodeFOMDato = LocalDate.of(2017, 2, 2)
            })

            val infotrygdForespResponse = deafaultInfotrygdForesp()
            infotrygdForespResponse.sMhistorikk = InfotrygdForesp.SMhistorikk().apply {
                sykmelding.add(TypeSMinfo().apply {
                    periode = TypeSMinfo.Periode().apply {
                        stans = "AF"
                        arbufoerTOM = LocalDate.of(2017, 2, 1)
                    }
                })
            }

            ValidationRuleChain.PERIOD_IS_AF(ruleData(healthInformation, infotrygdForespResponse)) shouldEqual false
        }

        it("Should check rule MAX_SICK_LEAVE_PAYOUT, should trigger rule") {
            val healthInformation = deafaultHelseOpplysningerArbeidsuforhet().apply {
                aktivitet = HelseOpplysningerArbeidsuforhet.Aktivitet().apply {
                    periode.add(HelseOpplysningerArbeidsuforhet.Aktivitet.Periode().apply {
                        periodeFOMDato = LocalDate.of(2017, 2, 1)
                        periodeTOMDato = LocalDate.of(2017, 2, 10)
                    })
                }
            }

            val infotrygdForespResponse = deafaultInfotrygdForesp()
            infotrygdForespResponse.sMhistorikk = InfotrygdForesp.SMhistorikk().apply {
                sykmelding.add(TypeSMinfo().apply {
                    periode = TypeSMinfo.Periode().apply {
                        arbufoerFOM = LocalDate.of(2017, 2, 2)
                        stans = "MAX"
                    }
                })
            }

            ValidationRuleChain.MAX_SICK_LEAVE_PAYOUT(ruleData(healthInformation, infotrygdForespResponse)) shouldEqual true
        }

        it("Should check rule MAX_SICK_LEAVE_PAYOUT, should NOT trigger rule") {
            val healthInformation = deafaultHelseOpplysningerArbeidsuforhet().apply {
                aktivitet = HelseOpplysningerArbeidsuforhet.Aktivitet().apply {
                    periode.add(HelseOpplysningerArbeidsuforhet.Aktivitet.Periode().apply {
                        periodeFOMDato = LocalDate.of(2017, 2, 11)
                        periodeTOMDato = LocalDate.of(2017, 2, 20)
                    })
                }
            }

            val infotrygdForespResponse = deafaultInfotrygdForesp()
            infotrygdForespResponse.sMhistorikk = InfotrygdForesp.SMhistorikk().apply {
                sykmelding.add(TypeSMinfo().apply {
                    periode = TypeSMinfo.Periode().apply {
                        arbufoerFOM = LocalDate.of(2017, 2, 2)
                        arbufoerFOM = LocalDate.of(2017, 2, 10)
                    }
                })

                sykmelding.add(TypeSMinfo().apply {
                    periode = TypeSMinfo.Periode().apply {
                        arbufoerFOM = LocalDate.of(2016, 2, 2)
                        arbufoerFOM = LocalDate.of(2016, 2, 10)
                        stans = "MAX"
                    }
                })
            }

            ValidationRuleChain.MAX_SICK_LEAVE_PAYOUT(ruleData(healthInformation, infotrygdForespResponse)) shouldEqual false
        }


        it("Should check rule MAX_SICK_LEAVE_PAYOUT, should NOT trigger rule") {
            val healthInformation = deafaultHelseOpplysningerArbeidsuforhet().apply {
                aktivitet = HelseOpplysningerArbeidsuforhet.Aktivitet().apply {
                    periode.add(HelseOpplysningerArbeidsuforhet.Aktivitet.Periode().apply {
                        periodeFOMDato = LocalDate.now()
                        periodeTOMDato = LocalDate.now().plusDays(10)
                    })
                }
            }

            val infotrygdForespResponse = deafaultInfotrygdForesp()
            infotrygdForespResponse.sMhistorikk = InfotrygdForesp.SMhistorikk().apply {
                sykmelding.add(TypeSMinfo().apply {
                    periode = TypeSMinfo.Periode().apply {
                        arbufoerFOM = LocalDate.now()
                        stans = "AAA"
                    }
                })
            }

            ValidationRuleChain.MAX_SICK_LEAVE_PAYOUT(ruleData(healthInformation, infotrygdForespResponse)) shouldEqual false
        }

        it("Should check rule MAX_SICK_LEAVE_PAYOUT, should NOT trigger rule") {
            val healthInformation = deafaultHelseOpplysningerArbeidsuforhet().apply {
                aktivitet = HelseOpplysningerArbeidsuforhet.Aktivitet().apply {
                    periode.add(HelseOpplysningerArbeidsuforhet.Aktivitet.Periode().apply {
                        periodeFOMDato = LocalDate.now()
                        periodeTOMDato = LocalDate.now().plusDays(10)
                    })
                }
            }

            val infotrygdForespResponse = deafaultInfotrygdForesp()
            infotrygdForespResponse.sMhistorikk = InfotrygdForesp.SMhistorikk().apply {
                sykmelding.add(TypeSMinfo().apply {
                    periode = TypeSMinfo.Periode().apply {
                        arbufoerFOM = LocalDate.now()
                        stans = "DOD"
                    }
                })
            }

            ValidationRuleChain.MAX_SICK_LEAVE_PAYOUT(ruleData(healthInformation, infotrygdForespResponse)) shouldEqual false
        }

        it("Should check rule MAX_SICK_LEAVE_PAYOUT, should NOT trigger rule") {
            val healthInformation = deafaultHelseOpplysningerArbeidsuforhet().apply {
                aktivitet = HelseOpplysningerArbeidsuforhet.Aktivitet().apply {
                    periode.add(HelseOpplysningerArbeidsuforhet.Aktivitet.Periode().apply {
                        periodeFOMDato = LocalDate.now()
                        periodeTOMDato = LocalDate.now().plusDays(10)
                    })
                }
            }

            val infotrygdForespResponse = deafaultInfotrygdForesp()
            infotrygdForespResponse.sMhistorikk = InfotrygdForesp.SMhistorikk().apply {
                sykmelding.add(TypeSMinfo().apply {
                    periode = TypeSMinfo.Periode().apply {
                        arbufoerFOM = LocalDate.now().plusDays(15)
                    }
                })
            }

            ValidationRuleChain.MAX_SICK_LEAVE_PAYOUT(ruleData(healthInformation, infotrygdForespResponse)) shouldEqual false
        }

        it("Should check rule MAX_SICK_LEAVE_PAYOUT, should NOT trigger rule") {
            val healthInformation = deafaultHelseOpplysningerArbeidsuforhet().apply {
                aktivitet = HelseOpplysningerArbeidsuforhet.Aktivitet().apply {
                    periode.add(HelseOpplysningerArbeidsuforhet.Aktivitet.Periode().apply {
                        periodeFOMDato = LocalDate.now()
                        periodeTOMDato = LocalDate.now().plusDays(10)
                    })
                }
            }

            val infotrygdForespResponse = deafaultInfotrygdForesp()

            ValidationRuleChain.MAX_SICK_LEAVE_PAYOUT(ruleData(healthInformation, infotrygdForespResponse)) shouldEqual false
        }

        it("Should check rule ERROR_FROM_IT_HOUVED_STATUS_KODEMELDING, should trigger rule") {
            val healthInformation = deafaultHelseOpplysningerArbeidsuforhet()

            val infotrygdForespResponse = deafaultInfotrygdForesp()
            infotrygdForespResponse.hovedStatus = StatusType().apply {
                kodeMelding = "5"
            }

            ValidationRuleChain.ERROR_FROM_IT_HOUVED_STATUS_KODEMELDING(ruleData(healthInformation, infotrygdForespResponse)) shouldEqual true
        }

        it("Should check rule ERROR_FROM_IT_HOUVED_STATUS_KODEMELDING, should NOT trigger rule") {
            val healthInformation = deafaultHelseOpplysningerArbeidsuforhet()

            val infotrygdForespResponse = deafaultInfotrygdForesp()
            infotrygdForespResponse.hovedStatus = StatusType().apply {
                kodeMelding = "0"
            }

            ValidationRuleChain.ERROR_FROM_IT_HOUVED_STATUS_KODEMELDING(ruleData(healthInformation, infotrygdForespResponse)) shouldEqual false
        }

        it("Should check rule ERROR_FROM_IT_SMHISTORIKK_STATUS_KODEMELDING, should trigger rule") {
            val healthInformation = deafaultHelseOpplysningerArbeidsuforhet()
            val infotrygdForespResponse = deafaultInfotrygdForesp()

            infotrygdForespResponse.sMhistorikk = InfotrygdForesp.SMhistorikk().apply {
                status = StatusType().apply {
                    kodeMelding = "5"
                }
            }

            ValidationRuleChain.ERROR_FROM_IT_SMHISTORIKK_STATUS_KODEMELDING(ruleData(healthInformation, infotrygdForespResponse)) shouldEqual true
        }

        it("Should check rule ERROR_FROM_IT_SMHISTORIKK_STATUS_KODEMELDING, should NOT trigger rule") {
            val healthInformation = deafaultHelseOpplysningerArbeidsuforhet()
            val infotrygdForespResponse = deafaultInfotrygdForesp()

            infotrygdForespResponse.sMhistorikk = InfotrygdForesp.SMhistorikk().apply {
                status = StatusType().apply {
                    kodeMelding = "0"
                }
            }

            ValidationRuleChain.ERROR_FROM_IT_SMHISTORIKK_STATUS_KODEMELDING(ruleData(healthInformation, infotrygdForespResponse)) shouldEqual false
        }

        it("Should check rule ERROR_FROM_IT_PARALELLYTELSER_STATUS_KODEMELDING, should trigger rule") {
            val healthInformation = deafaultHelseOpplysningerArbeidsuforhet()

            val infotrygdForespResponse = deafaultInfotrygdForesp()
            infotrygdForespResponse.parallelleYtelser = InfotrygdForesp.ParallelleYtelser().apply {
                status = StatusType().apply {
                    kodeMelding = "5"
                }
            }

            ValidationRuleChain.ERROR_FROM_IT_PARALELLYTELSER_STATUS_KODEMELDING(ruleData(healthInformation, infotrygdForespResponse)) shouldEqual true
        }

        it("Should check rule ERROR_FROM_IT_PARALELLYTELSER_STATUS_KODEMELDING, should NOT trigger rule") {
            val healthInformation = deafaultHelseOpplysningerArbeidsuforhet()

            val infotrygdForespResponse = deafaultInfotrygdForesp()
            infotrygdForespResponse.parallelleYtelser = InfotrygdForesp.ParallelleYtelser().apply {
                status = StatusType().apply {
                    kodeMelding = "0"
                }
            }

            ValidationRuleChain.ERROR_FROM_IT_PARALELLYTELSER_STATUS_KODEMELDING(ruleData(healthInformation, infotrygdForespResponse)) shouldEqual false
        }

        it("Should check rule ERROR_FROM_IT_DIAGNOSE_OK_UTREKK_STATUS_KODEMELDING, should trigger rule") {
            val healthInformation = deafaultHelseOpplysningerArbeidsuforhet()

            val infotrygdForespResponse = deafaultInfotrygdForesp()
            infotrygdForespResponse.diagnosekodeOK = InfotrygdForesp.DiagnosekodeOK().apply {
                status = StatusType().apply {
                    kodeMelding = "5"
                }
            }

            ValidationRuleChain.ERROR_FROM_IT_DIAGNOSE_OK_UTREKK_STATUS_KODEMELDING(ruleData(healthInformation, infotrygdForespResponse)) shouldEqual true
        }

        it("Should check rule ERROR_FROM_IT_DIAGNOSE_OK_UTREKK_STATUS_KODEMELDING, should NOT trigger rule") {
            val healthInformation = deafaultHelseOpplysningerArbeidsuforhet()

            val infotrygdForespResponse = deafaultInfotrygdForesp()
            infotrygdForespResponse.diagnosekodeOK = InfotrygdForesp.DiagnosekodeOK().apply {
                status = StatusType().apply {
                    kodeMelding = "0"
                }
            }

            ValidationRuleChain.ERROR_FROM_IT_DIAGNOSE_OK_UTREKK_STATUS_KODEMELDING(ruleData(healthInformation, infotrygdForespResponse)) shouldEqual false
        }

        it("Should check rule ERROR_FROM_IT_PASIENT_UTREKK_STATUS_KODEMELDING, should trigger rule") {
            val healthInformation = deafaultHelseOpplysningerArbeidsuforhet()

            val infotrygdForespResponse = deafaultInfotrygdForesp()
            infotrygdForespResponse.pasient = InfotrygdForesp.Pasient().apply {
                status = StatusType().apply {
                    kodeMelding = "5"
                }
            }

                ValidationRuleChain.ERROR_FROM_IT_PASIENT_UTREKK_STATUS_KODEMELDING(ruleData(healthInformation, infotrygdForespResponse)) shouldEqual true
            }

        it("Should check rule ERROR_FROM_IT_PASIENT_UTREKK_STATUS_KODEMELDING, should NOT trigger rule") {
            val healthInformation = deafaultHelseOpplysningerArbeidsuforhet()

            val infotrygdForespResponse = deafaultInfotrygdForesp()
            infotrygdForespResponse.pasient = InfotrygdForesp.Pasient().apply {
                status = StatusType().apply {
                    kodeMelding = "0"
                }
            }

            ValidationRuleChain.ERROR_FROM_IT_PASIENT_UTREKK_STATUS_KODEMELDING(ruleData(healthInformation, infotrygdForespResponse)) shouldEqual false
        }
    }
})
