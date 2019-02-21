package no.nav.syfo

import no.nav.helse.infotrygd.foresp.InfotrygdForesp
import no.nav.helse.infotrygd.foresp.StatusType
import no.nav.helse.infotrygd.foresp.TypeSMinfo
import no.nav.helse.sm2013.HelseOpplysningerArbeidsuforhet
import org.amshove.kluent.shouldEqual
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.LocalDate

object FindOprasjonstypeSpek : Spek({

    describe("Test the method FindOprasjonstypeSpek") {
        it("Should set oprasjonstype to 1, when kodemelding is 04 ") {
            val healthInformation = HelseOpplysningerArbeidsuforhet().apply {
                aktivitet = HelseOpplysningerArbeidsuforhet.Aktivitet().apply {
                    periode.add(
                            HelseOpplysningerArbeidsuforhet.Aktivitet.Periode().apply {
                                periodeFOMDato = LocalDate.now()
                                periodeTOMDato = LocalDate.now().plusDays(4)
                            }
                    )
                }
            }

            val infotrygdForesp = InfotrygdForesp().apply {
                sMhistorikk = InfotrygdForesp.SMhistorikk().apply {
                    sykmelding.add(TypeSMinfo().apply {
                        periode = TypeSMinfo.Periode().apply {
                            arbufoerFOM = LocalDate.now()
                            arbufoerTOM = LocalDate.now().plusDays(2)
                        }
                    })
                    status = StatusType().apply {
                        kodeMelding = "04"
                    }
                }
            }

            findOprasjonstype(healthInformation.aktivitet.periode.first(),
                    InfotrygdForespAndHealthInformation(infotrygdForesp, healthInformation)) shouldEqual 1.toBigInteger()
        }

        it("Should set oprasjonstype to 2") {
            val healthInformation = HelseOpplysningerArbeidsuforhet().apply {
                aktivitet = HelseOpplysningerArbeidsuforhet.Aktivitet().apply {
                    periode.add(
                            HelseOpplysningerArbeidsuforhet.Aktivitet.Periode().apply {
                                periodeFOMDato = LocalDate.of(2019, 1, 14)
                                periodeTOMDato = LocalDate.of(2019, 1, 20)
                            }
                    )
                }
            }

            val infotrygdForesp = InfotrygdForesp().apply {
                sMhistorikk = InfotrygdForesp.SMhistorikk().apply {
                    sykmelding.add(TypeSMinfo().apply {
                        periode = TypeSMinfo.Periode().apply {
                            arbufoerFOM = LocalDate.of(2019, 1, 9)
                            arbufoerTOM = LocalDate.of(2019, 1, 13)
                        }
                    })
                    status = StatusType().apply {
                        kodeMelding = "00"
                    }
                }
            }

            findOprasjonstype(healthInformation.aktivitet.periode.first(),
                    InfotrygdForespAndHealthInformation(infotrygdForesp, healthInformation)) shouldEqual 2.toBigInteger()
        }

        it("Should set oprasjonstype to 2") {
            val healthInformation = HelseOpplysningerArbeidsuforhet().apply {
                aktivitet = HelseOpplysningerArbeidsuforhet.Aktivitet().apply {
                    periode.add(
                            HelseOpplysningerArbeidsuforhet.Aktivitet.Periode().apply {
                                periodeFOMDato = LocalDate.of(2019, 2, 20)
                                periodeTOMDato = LocalDate.of(2019, 3, 29)
                            }
                    )
                }
            }

            val infotrygdForesp = InfotrygdForesp().apply {
                sMhistorikk = InfotrygdForesp.SMhistorikk().apply {
                    sykmelding.add(TypeSMinfo().apply {
                        periode = TypeSMinfo.Periode().apply {
                            arbufoerFOM = LocalDate.of(2017, 4, 20)
                            arbufoerTOM = LocalDate.of(2017, 5, 29)
                        }
                    })
                    status = StatusType().apply {
                        kodeMelding = "00"
                    }
                }
            }

            findOprasjonstype(healthInformation.aktivitet.periode.first(),
                    InfotrygdForespAndHealthInformation(infotrygdForesp, healthInformation)) shouldEqual 2.toBigInteger()
        }

        it("Should set oprasjonstype to 3") {
            val healthInformation = HelseOpplysningerArbeidsuforhet().apply {
                aktivitet = HelseOpplysningerArbeidsuforhet.Aktivitet().apply {
                    periode.add(
                            HelseOpplysningerArbeidsuforhet.Aktivitet.Periode().apply {
                                periodeFOMDato = LocalDate.of(2018, 10, 9)
                                periodeTOMDato = LocalDate.of(2018, 11, 11)
                            }
                    )
                }
            }

            val infotrygdForesp = InfotrygdForesp().apply {
                sMhistorikk = InfotrygdForesp.SMhistorikk().apply {
                    sykmelding.add(TypeSMinfo().apply {
                        periode = TypeSMinfo.Periode().apply {
                            arbufoerFOM = LocalDate.of(2018, 10, 9)
                            arbufoerTOM = LocalDate.of(2019, 1, 1)
                        }
                    })
                    status = StatusType().apply {
                        kodeMelding = "00"
                    }
                }
            }

            findOprasjonstype(healthInformation.aktivitet.periode.first(),
                    InfotrygdForespAndHealthInformation(infotrygdForesp, healthInformation)) shouldEqual 3.toBigInteger()
        }

        it("Should set oprasjonstype to 1") {
            val healthInformation = HelseOpplysningerArbeidsuforhet().apply {
                aktivitet = HelseOpplysningerArbeidsuforhet.Aktivitet().apply {
                    periode.add(
                            HelseOpplysningerArbeidsuforhet.Aktivitet.Periode().apply {
                                periodeFOMDato = LocalDate.of(2019, 2, 22)
                                periodeTOMDato = LocalDate.of(2019, 3, 7)
                            }
                    )
                }
            }

            val infotrygdForesp = InfotrygdForesp().apply {
                sMhistorikk = InfotrygdForesp.SMhistorikk().apply {
                    sykmelding.add(TypeSMinfo().apply {
                        periode = TypeSMinfo.Periode().apply {
                            arbufoerFOM = LocalDate.of(2016, 7, 10)

                        }
                    })
                    status = StatusType().apply {
                        kodeMelding = "00"
                    }
                }
            }

            findOprasjonstype(healthInformation.aktivitet.periode.first(),
                    InfotrygdForespAndHealthInformation(infotrygdForesp, healthInformation)) shouldEqual 1.toBigInteger()
        }
    }
})