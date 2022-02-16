package no.nav.syfo

import io.mockk.mockk
import no.nav.helse.infotrygd.foresp.InfotrygdForesp
import no.nav.helse.infotrygd.foresp.StatusType
import no.nav.helse.infotrygd.foresp.TypeSMinfo
import no.nav.helse.sm2013.HelseOpplysningerArbeidsuforhet
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.client.ManuellClient
import no.nav.syfo.client.NorskHelsenettClient
import no.nav.syfo.model.OpprettOppgaveKafkaMessage
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.services.BehandlingsutfallService
import no.nav.syfo.services.UpdateInfotrygdService
import no.nav.syfo.util.LoggingMeta
import org.amshove.kluent.shouldBeEqualTo
import org.apache.kafka.clients.producer.KafkaProducer
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.LocalDate

object FindOprasjonstypeSpek : Spek({
    val manuellClient = mockk<ManuellClient>()
    val norskHelsenettClient = mockk<NorskHelsenettClient>()
    val kafkaAivenProducerReceivedSykmelding = mockk<KafkaProducer<String, ReceivedSykmelding>>()
    val kafkaAivenProducerOppgave = mockk<KafkaProducer<String, OpprettOppgaveKafkaMessage>>()
    val behandlingsutfallService = mockk<BehandlingsutfallService>()
    val updateInfotrygdService = UpdateInfotrygdService(
        manuellClient,
        norskHelsenettClient,
        ApplicationState(alive = true, ready = true),
        kafkaAivenProducerReceivedSykmelding,
        kafkaAivenProducerOppgave,
        "retry",
        "oppgave",
        behandlingsutfallService
    )

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
                    sykmelding.add(
                        TypeSMinfo().apply {
                            periode = TypeSMinfo.Periode().apply {
                                arbufoerFOM = LocalDate.now()
                                arbufoerTOM = LocalDate.now().plusDays(2)
                            }
                        }
                    )
                    status = StatusType().apply {
                        kodeMelding = "04"
                    }
                }
            }

            updateInfotrygdService.findOperasjonstype(
                healthInformation.aktivitet.periode.first(),
                InfotrygdForespAndHealthInformation(infotrygdForesp, healthInformation), LoggingMeta("mottakId", "12315", "", "")
            ) shouldBeEqualTo 1
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
                    sykmelding.add(
                        TypeSMinfo().apply {
                            periode = TypeSMinfo.Periode().apply {
                                arbufoerFOM = LocalDate.of(2019, 1, 9)
                                arbufoerTOM = LocalDate.of(2019, 1, 13)
                            }
                        }
                    )
                    status = StatusType().apply {
                        kodeMelding = "00"
                    }
                }
            }

            updateInfotrygdService.findOperasjonstype(
                healthInformation.aktivitet.periode.first(),
                InfotrygdForespAndHealthInformation(infotrygdForesp, healthInformation), LoggingMeta("mottakId", "12315", "", "")
            ) shouldBeEqualTo 2
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
                    sykmelding.add(
                        TypeSMinfo().apply {
                            periode = TypeSMinfo.Periode().apply {
                                arbufoerFOM = LocalDate.of(2019, 2, 10)
                                arbufoerTOM = LocalDate.of(2019, 2, 20)
                            }
                        }
                    )
                    status = StatusType().apply {
                        kodeMelding = "00"
                    }
                }
            }

            updateInfotrygdService.findOperasjonstype(
                healthInformation.aktivitet.periode.first(),
                InfotrygdForespAndHealthInformation(infotrygdForesp, healthInformation), LoggingMeta("mottakId", "12315", "", "")
            ) shouldBeEqualTo 2
        }

        it("Når opphold kun skyldes helg skal operasjonstype være 2") {
            val healthInformation = HelseOpplysningerArbeidsuforhet().apply {
                aktivitet = HelseOpplysningerArbeidsuforhet.Aktivitet().apply {
                    periode.add(
                        HelseOpplysningerArbeidsuforhet.Aktivitet.Periode().apply {
                            periodeFOMDato = LocalDate.of(2022, 2, 14) // mandag
                            periodeTOMDato = LocalDate.of(2022, 2, 20)
                        }
                    )
                }
            }

            val infotrygdForesp = InfotrygdForesp().apply {
                sMhistorikk = InfotrygdForesp.SMhistorikk().apply {
                    sykmelding.add(
                        TypeSMinfo().apply {
                            periode = TypeSMinfo.Periode().apply {
                                arbufoerFOM = LocalDate.of(2022, 2, 1)
                                arbufoerTOM = LocalDate.of(2022, 2, 11) // fredag
                            }
                        }
                    )
                    status = StatusType().apply {
                        kodeMelding = "00"
                    }
                }
            }

            updateInfotrygdService.findOperasjonstype(
                healthInformation.aktivitet.periode.first(),
                InfotrygdForespAndHealthInformation(infotrygdForesp, healthInformation), LoggingMeta("mottakId", "12315", "", "")
            ) shouldBeEqualTo 2
        }

        it("Når opphold kun skyldes lørdag skal operasjonstype være 2") {
            val healthInformation = HelseOpplysningerArbeidsuforhet().apply {
                aktivitet = HelseOpplysningerArbeidsuforhet.Aktivitet().apply {
                    periode.add(
                        HelseOpplysningerArbeidsuforhet.Aktivitet.Periode().apply {
                            periodeFOMDato = LocalDate.of(2022, 2, 13) // søndag
                            periodeTOMDato = LocalDate.of(2022, 2, 20)
                        }
                    )
                }
            }

            val infotrygdForesp = InfotrygdForesp().apply {
                sMhistorikk = InfotrygdForesp.SMhistorikk().apply {
                    sykmelding.add(
                        TypeSMinfo().apply {
                            periode = TypeSMinfo.Periode().apply {
                                arbufoerFOM = LocalDate.of(2022, 2, 1)
                                arbufoerTOM = LocalDate.of(2022, 2, 11) // fredag
                            }
                        }
                    )
                    status = StatusType().apply {
                        kodeMelding = "00"
                    }
                }
            }

            updateInfotrygdService.findOperasjonstype(
                healthInformation.aktivitet.periode.first(),
                InfotrygdForespAndHealthInformation(infotrygdForesp, healthInformation), LoggingMeta("mottakId", "12315", "", "")
            ) shouldBeEqualTo 2
        }

        it("Når opphold kun skyldes søndag skal operasjonstype være 2") {
            val healthInformation = HelseOpplysningerArbeidsuforhet().apply {
                aktivitet = HelseOpplysningerArbeidsuforhet.Aktivitet().apply {
                    periode.add(
                        HelseOpplysningerArbeidsuforhet.Aktivitet.Periode().apply {
                            periodeFOMDato = LocalDate.of(2022, 2, 14) // mandag
                            periodeTOMDato = LocalDate.of(2022, 2, 20)
                        }
                    )
                }
            }

            val infotrygdForesp = InfotrygdForesp().apply {
                sMhistorikk = InfotrygdForesp.SMhistorikk().apply {
                    sykmelding.add(
                        TypeSMinfo().apply {
                            periode = TypeSMinfo.Periode().apply {
                                arbufoerFOM = LocalDate.of(2022, 2, 1)
                                arbufoerTOM = LocalDate.of(2022, 2, 12) // lørdag
                            }
                        }
                    )
                    status = StatusType().apply {
                        kodeMelding = "00"
                    }
                }
            }

            updateInfotrygdService.findOperasjonstype(
                healthInformation.aktivitet.periode.first(),
                InfotrygdForespAndHealthInformation(infotrygdForesp, healthInformation), LoggingMeta("mottakId", "12315", "", "")
            ) shouldBeEqualTo 2
        }

        it("To dagers opphold som ikke skyldes helg skal gi operasjonstype være 1") {
            val healthInformation = HelseOpplysningerArbeidsuforhet().apply {
                aktivitet = HelseOpplysningerArbeidsuforhet.Aktivitet().apply {
                    periode.add(
                        HelseOpplysningerArbeidsuforhet.Aktivitet.Periode().apply {
                            periodeFOMDato = LocalDate.of(2022, 2, 17) // torsdag
                            periodeTOMDato = LocalDate.of(2022, 2, 20)
                        }
                    )
                }
            }

            val infotrygdForesp = InfotrygdForesp().apply {
                sMhistorikk = InfotrygdForesp.SMhistorikk().apply {
                    sykmelding.add(
                        TypeSMinfo().apply {
                            periode = TypeSMinfo.Periode().apply {
                                arbufoerFOM = LocalDate.of(2022, 2, 1)
                                arbufoerTOM = LocalDate.of(2022, 2, 14) // mandag
                            }
                        }
                    )
                    status = StatusType().apply {
                        kodeMelding = "00"
                    }
                }
            }

            updateInfotrygdService.findOperasjonstype(
                healthInformation.aktivitet.periode.first(),
                InfotrygdForespAndHealthInformation(infotrygdForesp, healthInformation), LoggingMeta("mottakId", "12315", "", "")
            ) shouldBeEqualTo 1
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
                    sykmelding.add(
                        TypeSMinfo().apply {
                            periode = TypeSMinfo.Periode().apply {
                                arbufoerFOM = LocalDate.of(2018, 10, 9)
                                arbufoerTOM = LocalDate.of(2019, 1, 1)
                            }
                        }
                    )
                    status = StatusType().apply {
                        kodeMelding = "00"
                    }
                }
            }

            updateInfotrygdService.findOperasjonstype(
                healthInformation.aktivitet.periode.first(),
                InfotrygdForespAndHealthInformation(infotrygdForesp, healthInformation), LoggingMeta("mottakId", "12315", "", "")
            ) shouldBeEqualTo 3
        }

        it("Should set oprasjonstype to 3") {
            val healthInformation = HelseOpplysningerArbeidsuforhet().apply {
                aktivitet = HelseOpplysningerArbeidsuforhet.Aktivitet().apply {
                    periode.add(
                        HelseOpplysningerArbeidsuforhet.Aktivitet.Periode().apply {
                            periodeFOMDato = LocalDate.of(2019, 1, 1)
                            periodeTOMDato = LocalDate.of(2019, 1, 9)
                        }
                    )
                }
            }

            val infotrygdForesp = InfotrygdForesp().apply {
                sMhistorikk = InfotrygdForesp.SMhistorikk().apply {
                    sykmelding.add(
                        TypeSMinfo().apply {
                            periode = TypeSMinfo.Periode().apply {
                                arbufoerFOM = LocalDate.of(2019, 1, 1)
                                arbufoerTOM = LocalDate.of(2019, 1, 10)
                            }
                        }
                    )
                    status = StatusType().apply {
                        kodeMelding = "00"
                    }
                }
            }

            updateInfotrygdService.findOperasjonstype(
                healthInformation.aktivitet.periode.first(),
                InfotrygdForespAndHealthInformation(infotrygdForesp, healthInformation), LoggingMeta("mottakId", "12315", "", "")
            ) shouldBeEqualTo 3
        }

        it("Should set oprasjonstype to 3") {
            val healthInformation = HelseOpplysningerArbeidsuforhet().apply {
                aktivitet = HelseOpplysningerArbeidsuforhet.Aktivitet().apply {
                    periode.add(
                        HelseOpplysningerArbeidsuforhet.Aktivitet.Periode().apply {
                            periodeFOMDato = LocalDate.of(2019, 3, 25)
                            periodeTOMDato = LocalDate.of(2019, 4, 30)
                            gradertSykmelding = HelseOpplysningerArbeidsuforhet.Aktivitet.Periode.GradertSykmelding().apply {
                                sykmeldingsgrad = 50
                            }
                        }
                    )
                }
            }

            val infotrygdForesp = InfotrygdForesp().apply {
                sMhistorikk = InfotrygdForesp.SMhistorikk().apply {
                    sykmelding.add(
                        TypeSMinfo().apply {
                            periode = TypeSMinfo.Periode().apply {
                                arbufoerFOM = LocalDate.of(2019, 3, 25)
                                arbufoerTOM = LocalDate.of(2019, 4, 30)
                                ufoeregrad = 60.toBigInteger()
                            }
                        }
                    )
                    status = StatusType().apply {
                        kodeMelding = "00"
                    }
                }
            }

            updateInfotrygdService.findOperasjonstype(
                healthInformation.aktivitet.periode.first(),
                InfotrygdForespAndHealthInformation(infotrygdForesp, healthInformation), LoggingMeta("mottakId", "12315", "", "")
            ) shouldBeEqualTo 3
        }

        it("Should set oprasjonstype to 3") {
            val healthInformation = HelseOpplysningerArbeidsuforhet().apply {
                aktivitet = HelseOpplysningerArbeidsuforhet.Aktivitet().apply {
                    periode.add(
                        HelseOpplysningerArbeidsuforhet.Aktivitet.Periode().apply {
                            periodeFOMDato = LocalDate.of(2019, 5, 22)
                            periodeTOMDato = LocalDate.of(2019, 6, 14)
                        }
                    )
                }
            }

            val infotrygdForesp = InfotrygdForesp().apply {
                sMhistorikk = InfotrygdForesp.SMhistorikk().apply {
                    sykmelding.add(
                        TypeSMinfo().apply {
                            periode = TypeSMinfo.Periode().apply {
                                arbufoerFOM = LocalDate.of(2019, 5, 22)
                                arbufoerTOM = LocalDate.of(2019, 5, 26)
                            }
                        }
                    )
                    status = StatusType().apply {
                        kodeMelding = "00"
                    }
                }
            }

            updateInfotrygdService.findOperasjonstype(
                healthInformation.aktivitet.periode.first(),
                InfotrygdForespAndHealthInformation(infotrygdForesp, healthInformation), LoggingMeta("mottakId", "12315", "", "")
            ) shouldBeEqualTo 3
        }

        it("Should set oprasjonstype to 3") {
            val healthInformation = HelseOpplysningerArbeidsuforhet().apply {
                aktivitet = HelseOpplysningerArbeidsuforhet.Aktivitet().apply {
                    periode.add(
                        HelseOpplysningerArbeidsuforhet.Aktivitet.Periode().apply {
                            periodeFOMDato = LocalDate.of(2019, 5, 20)
                            periodeTOMDato = LocalDate.of(2019, 6, 2)
                        }
                    )
                    periode.add(
                        HelseOpplysningerArbeidsuforhet.Aktivitet.Periode().apply {
                            periodeFOMDato = LocalDate.of(2019, 6, 3)
                            periodeTOMDato = LocalDate.of(2019, 6, 30)
                            gradertSykmelding = HelseOpplysningerArbeidsuforhet.Aktivitet.Periode.GradertSykmelding().apply {
                                sykmeldingsgrad = 80
                            }
                        }

                    )
                }
            }

            val infotrygdForesp = InfotrygdForesp().apply {
                sMhistorikk = InfotrygdForesp.SMhistorikk().apply {
                    sykmelding.add(
                        TypeSMinfo().apply {
                            periode = TypeSMinfo.Periode().apply {
                                arbufoerFOM = LocalDate.of(2018, 8, 21)
                                arbufoerTOM = LocalDate.of(2019, 5, 31)
                                ufoeregrad = 80.toBigInteger()
                            }
                        }
                    )
                    status = StatusType().apply {
                        kodeMelding = "00"
                    }
                }
            }

            updateInfotrygdService.findOperasjonstype(
                healthInformation.aktivitet.periode.first(),
                InfotrygdForespAndHealthInformation(infotrygdForesp, healthInformation), LoggingMeta("mottakId", "12315", "", "")
            ) shouldBeEqualTo 3
        }

        it("Should set oprasjonstype to 2") {
            val healthInformation = HelseOpplysningerArbeidsuforhet().apply {
                aktivitet = HelseOpplysningerArbeidsuforhet.Aktivitet().apply {
                    periode.add(
                        HelseOpplysningerArbeidsuforhet.Aktivitet.Periode().apply {
                            periodeFOMDato = LocalDate.of(2019, 6, 1)
                            periodeTOMDato = LocalDate.of(2019, 6, 30)
                        }
                    )
                }
            }

            val infotrygdForesp = InfotrygdForesp().apply {
                sMhistorikk = InfotrygdForesp.SMhistorikk().apply {
                    sykmelding.add(
                        TypeSMinfo().apply {
                            periode = TypeSMinfo.Periode().apply {
                                arbufoerFOM = LocalDate.of(2019, 5, 1)
                                arbufoerTOM = LocalDate.of(2019, 5, 31)
                            }
                        }
                    )
                    status = StatusType().apply {
                        kodeMelding = "00"
                    }
                }
            }

            updateInfotrygdService.findOperasjonstype(
                healthInformation.aktivitet.periode.first(),
                InfotrygdForespAndHealthInformation(infotrygdForesp, healthInformation), LoggingMeta("mottakId", "12315", "", "")
            ) shouldBeEqualTo 2
        }

        it("Should set oprasjonstype to 1") {
            val healthInformation = HelseOpplysningerArbeidsuforhet().apply {
                aktivitet = HelseOpplysningerArbeidsuforhet.Aktivitet().apply {
                    periode.add(
                        HelseOpplysningerArbeidsuforhet.Aktivitet.Periode().apply {
                            periodeFOMDato = LocalDate.of(2019, 8, 10)
                            periodeTOMDato = LocalDate.of(2019, 8, 10)
                        }
                    )
                }
            }

            val infotrygdForesp = InfotrygdForesp().apply {
                sMhistorikk = InfotrygdForesp.SMhistorikk().apply {
                    sykmelding.add(
                        TypeSMinfo().apply {
                            periode = TypeSMinfo.Periode().apply {
                                arbufoerFOM = LocalDate.of(2019, 8, 12)
                                arbufoerTOM = LocalDate.of(2019, 8, 12)
                            }
                        }
                    )
                    status = StatusType().apply {
                        kodeMelding = "00"
                    }
                }
            }

            updateInfotrygdService.findOperasjonstype(
                healthInformation.aktivitet.periode.first(),
                InfotrygdForespAndHealthInformation(infotrygdForesp, healthInformation), LoggingMeta("mottakId", "12315", "", "")
            ) shouldBeEqualTo 1
        }

        it("Should set oprasjonstype to 1") {
            val healthInformation = HelseOpplysningerArbeidsuforhet().apply {
                aktivitet = HelseOpplysningerArbeidsuforhet.Aktivitet().apply {
                    periode.add(
                        HelseOpplysningerArbeidsuforhet.Aktivitet.Periode().apply {
                            periodeFOMDato = LocalDate.of(2019, 8, 15)
                            periodeTOMDato = LocalDate.of(2019, 8, 15)
                        }
                    )
                }
            }

            val infotrygdForesp = InfotrygdForesp().apply {
                sMhistorikk = InfotrygdForesp.SMhistorikk().apply {
                    sykmelding.add(
                        TypeSMinfo().apply {
                            periode = TypeSMinfo.Periode().apply {
                                arbufoerFOM = LocalDate.of(2019, 8, 16)
                                arbufoerTOM = LocalDate.of(2019, 8, 16)
                            }
                        }
                    )
                    status = StatusType().apply {
                        kodeMelding = "00"
                    }
                }
            }

            updateInfotrygdService.findOperasjonstype(
                healthInformation.aktivitet.periode.first(),
                InfotrygdForespAndHealthInformation(infotrygdForesp, healthInformation), LoggingMeta("mottakId", "12315", "", "")
            ) shouldBeEqualTo 1
        }

        it("Should set oprasjonstype to 1") {
            val healthInformation = HelseOpplysningerArbeidsuforhet().apply {
                aktivitet = HelseOpplysningerArbeidsuforhet.Aktivitet().apply {
                    periode.add(
                        HelseOpplysningerArbeidsuforhet.Aktivitet.Periode().apply {
                            periodeFOMDato = LocalDate.of(2019, 8, 10)
                            periodeTOMDato = LocalDate.of(2019, 8, 10)
                        }
                    )
                }
            }

            val infotrygdForesp = InfotrygdForesp().apply {
                sMhistorikk = InfotrygdForesp.SMhistorikk().apply {
                    sykmelding.add(
                        TypeSMinfo().apply {
                            periode = TypeSMinfo.Periode().apply {
                                arbufoerFOM = LocalDate.of(2019, 8, 16)
                                arbufoerTOM = LocalDate.of(2019, 8, 16)
                            }
                        }
                    )
                    status = StatusType().apply {
                        kodeMelding = "00"
                    }
                }
            }

            updateInfotrygdService.findOperasjonstype(
                healthInformation.aktivitet.periode.first(),
                InfotrygdForespAndHealthInformation(infotrygdForesp, healthInformation), LoggingMeta("mottakId", "12315", "", "")
            ) shouldBeEqualTo 1
        }

        it("Should set oprasjonstype to 1") {
            val healthInformation = HelseOpplysningerArbeidsuforhet().apply {
                aktivitet = HelseOpplysningerArbeidsuforhet.Aktivitet().apply {
                    periode.add(
                        HelseOpplysningerArbeidsuforhet.Aktivitet.Periode().apply {
                            periodeFOMDato = LocalDate.of(2019, 8, 14)
                            periodeTOMDato = LocalDate.of(2019, 8, 17)
                        }
                    )
                }
            }

            val infotrygdForesp = InfotrygdForesp().apply {
                sMhistorikk = InfotrygdForesp.SMhistorikk().apply {
                    sykmelding.add(
                        TypeSMinfo().apply {
                            periode = TypeSMinfo.Periode().apply {
                                arbufoerFOM = LocalDate.of(2019, 3, 4)
                                arbufoerTOM = LocalDate.of(2019, 3, 7)
                            }
                        }
                    )
                    status = StatusType().apply {
                        kodeMelding = "00"
                    }
                }
            }

            updateInfotrygdService.findOperasjonstype(
                healthInformation.aktivitet.periode.first(),
                InfotrygdForespAndHealthInformation(infotrygdForesp, healthInformation), LoggingMeta("mottakId", "12315", "", "")
            ) shouldBeEqualTo 1
        }
    }
})
