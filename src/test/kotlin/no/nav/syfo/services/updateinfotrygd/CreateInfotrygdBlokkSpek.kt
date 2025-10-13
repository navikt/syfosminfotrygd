package no.nav.syfo.services.updateinfotrygd

import io.kotest.core.spec.style.FunSpec
import java.time.LocalDate
import no.nav.helse.infotrygd.foresp.InfotrygdForesp
import no.nav.helse.infotrygd.foresp.StatusType
import no.nav.helse.infotrygd.foresp.TypeSMinfo
import no.nav.helse.sm2013.HelseOpplysningerArbeidsuforhet
import no.nav.syfo.InfotrygdForespAndHealthInformation
import no.nav.syfo.UTENLANDSK_SYKEHUS
import no.nav.syfo.rules.validation.sortedSMInfos
import no.nav.syfo.sortedFOMDate
import no.nav.syfo.util.LoggingMeta
import org.amshove.kluent.shouldBeEqualTo

class CreateInfotrygdBlokkSpek :
    FunSpec({
        context("Testing the creating of infotrygdblokk") {
            test("Should set forsteFravaersDag correctly, when oprasjosntype 1") {
                val healthInformation =
                    HelseOpplysningerArbeidsuforhet().apply {
                        aktivitet =
                            HelseOpplysningerArbeidsuforhet.Aktivitet().apply {
                                periode.add(
                                    HelseOpplysningerArbeidsuforhet.Aktivitet.Periode().apply {
                                        periodeFOMDato = LocalDate.of(2019, 1, 1)
                                        periodeTOMDato = LocalDate.of(2019, 1, 2)
                                    },
                                )
                            }
                    }

                val infotrygdForesp =
                    InfotrygdForesp().apply {
                        sMhistorikk =
                            InfotrygdForesp.SMhistorikk().apply {
                                status = StatusType().apply { kodeMelding = "04" }
                            }
                    }

                val ifth = InfotrygdForespAndHealthInformation(infotrygdForesp, healthInformation)

                val perioder =
                    ifth.healthInformation.aktivitet.periode.sortedBy { it.periodeFOMDato }
                val operasjonstypeAndFom =
                    findoperasjonstypeAndFom(
                        healthInformation.aktivitet.periode.sortedFOMDate().first(),
                        healthInformation.aktivitet.periode.maxOf { it.periodeTOMDato },
                        infotrygdForesp.getInfotrygdPerioder()
                    )
                val forsteFravaersDag = operasjonstypeAndFom.second

                val infotrygdBlokk =
                    createInfotrygdBlokk(
                        ifth,
                        healthInformation.aktivitet.periode.last(),
                        "2134",
                        LocalDate.now(),
                        "LE",
                        "12345",
                        LoggingMeta("mottakId", "12315", "", ""),
                        "1234",
                        "NAV IKT",
                        behandletAvManuell = false,
                        utenlandskSykmelding = false,
                        operasjonstypeAndFom = operasjonstypeAndFom,
                    )

                infotrygdBlokk.forsteFravaersDag shouldBeEqualTo
                    ifth.healthInformation.aktivitet.periode.sortedFOMDate().first()
            }

            test(
                "Should set forsteFravaersDag correctly, when oprasjosntype 2 and more than 1 periode"
            ) {
                val healthInformation =
                    HelseOpplysningerArbeidsuforhet().apply {
                        aktivitet =
                            HelseOpplysningerArbeidsuforhet.Aktivitet().apply {
                                periode.add(
                                    HelseOpplysningerArbeidsuforhet.Aktivitet.Periode().apply {
                                        periodeFOMDato = LocalDate.of(2019, 1, 2)
                                        periodeTOMDato = LocalDate.of(2019, 1, 3)
                                    },
                                )
                                periode.add(
                                    HelseOpplysningerArbeidsuforhet.Aktivitet.Periode().apply {
                                        periodeFOMDato = LocalDate.of(2019, 1, 4)
                                        periodeTOMDato = LocalDate.of(2019, 1, 5)
                                    },
                                )
                            }
                    }

                val infotrygdForesp =
                    InfotrygdForesp().apply {
                        sMhistorikk =
                            InfotrygdForesp.SMhistorikk().apply {
                                sykmelding.add(
                                    TypeSMinfo().apply {
                                        periode =
                                            TypeSMinfo.Periode().apply {
                                                arbufoerFOM = LocalDate.of(2019, 1, 1)
                                                arbufoerTOM = LocalDate.of(2019, 1, 1)
                                            }
                                    },
                                )
                                status = StatusType().apply { kodeMelding = "00" }
                            }
                    }

                val ifth = InfotrygdForespAndHealthInformation(infotrygdForesp, healthInformation)

                val perioder =
                    ifth.healthInformation.aktivitet.periode.sortedBy { it.periodeFOMDato }
                val operasjonstypeAndFom =
                    findoperasjonstypeAndFom(
                        healthInformation.aktivitet.periode.sortedFOMDate().first(),
                        healthInformation.aktivitet.periode.maxOf { it.periodeTOMDato },
                        infotrygdForesp.getInfotrygdPerioder()
                    )
                val forsteFravaersDag = operasjonstypeAndFom.second

                val infotrygdfirstBlokk =
                    createInfotrygdBlokk(
                        ifth,
                        healthInformation.aktivitet.periode.first(),
                        "2134",
                        LocalDate.now(),
                        "LE",
                        "12345",
                        LoggingMeta("mottakId", "12315", "", ""),
                        "1234",
                        "NAV IKT",
                        behandletAvManuell = false,
                        utenlandskSykmelding = false,
                        operasjonstypeAndFom,
                    )

                val infotrygdlastBlokk =
                    createInfotrygdBlokk(
                        ifth,
                        healthInformation.aktivitet.periode.last(),
                        "2134",
                        LocalDate.now(),
                        "LE",
                        "12345",
                        LoggingMeta("mottakId", "12315", "", ""),
                        "1234",
                        "NAV IKT",
                        behandletAvManuell = false,
                        utenlandskSykmelding = false,
                        operasjonstypeAndFom,
                    )
                val infotrygdSykmelding =
                    ifth.infotrygdForesp.getInfotrygdPerioder().sortedSMInfos()
                infotrygdfirstBlokk.forsteFravaersDag shouldBeEqualTo
                    infotrygdSykmelding.last().periode.arbufoerFOM
                infotrygdlastBlokk.forsteFravaersDag shouldBeEqualTo
                    infotrygdSykmelding.last().periode.arbufoerFOM
            }

            test(
                "Should set forsteFravaersDag correctly, when oprasjosntype 1 and more than 1 periode"
            ) {
                val healthInformation =
                    HelseOpplysningerArbeidsuforhet().apply {
                        aktivitet =
                            HelseOpplysningerArbeidsuforhet.Aktivitet().apply {
                                periode.add(
                                    HelseOpplysningerArbeidsuforhet.Aktivitet.Periode().apply {
                                        periodeFOMDato = LocalDate.of(2019, 1, 3)
                                        periodeTOMDato = LocalDate.of(2019, 1, 4)
                                    },
                                )
                                periode.add(
                                    HelseOpplysningerArbeidsuforhet.Aktivitet.Periode().apply {
                                        periodeFOMDato = LocalDate.of(2019, 1, 5)
                                        periodeTOMDato = LocalDate.of(2019, 1, 6)
                                    },
                                )
                            }
                    }

                val infotrygdForesp =
                    InfotrygdForesp().apply {
                        sMhistorikk =
                            InfotrygdForesp.SMhistorikk().apply {
                                sykmelding.add(
                                    TypeSMinfo().apply {
                                        periode =
                                            TypeSMinfo.Periode().apply {
                                                arbufoerFOM = LocalDate.of(2019, 1, 1)
                                                arbufoerTOM = LocalDate.of(2019, 1, 1)
                                            }
                                    },
                                )
                                status = StatusType().apply { kodeMelding = "00" }
                            }
                    }

                val ifth = InfotrygdForespAndHealthInformation(infotrygdForesp, healthInformation)

                val perioder =
                    ifth.healthInformation.aktivitet.periode.sortedBy { it.periodeFOMDato }
                val operasjonstypeAndFom =
                    findoperasjonstypeAndFom(
                        healthInformation.aktivitet.periode.sortedFOMDate().first(),
                        healthInformation.aktivitet.periode.maxOf { it.periodeTOMDato },
                        infotrygdForesp.getInfotrygdPerioder()
                    )
                val forsteFravaersDag = operasjonstypeAndFom.second

                val infotrygdfirstBlokk =
                    createInfotrygdBlokk(
                        ifth,
                        healthInformation.aktivitet.periode.first(),
                        "2134",
                        LocalDate.now(),
                        "LE",
                        "12345",
                        LoggingMeta("mottakId", "12315", "", ""),
                        "1234",
                        "NAV IKT",
                        behandletAvManuell = false,
                        utenlandskSykmelding = false,
                        operasjonstypeAndFom,
                    )

                val infotrygdlastBlokk =
                    createInfotrygdBlokk(
                        ifth,
                        healthInformation.aktivitet.periode.last(),
                        "2134",
                        LocalDate.now(),
                        "LE",
                        "12345",
                        LoggingMeta("mottakId", "12315", "", ""),
                        "1234",
                        "NAV IKT",
                        behandletAvManuell = false,
                        utenlandskSykmelding = false,
                        operasjonstypeAndFom,
                    )

                infotrygdfirstBlokk.forsteFravaersDag shouldBeEqualTo
                    ifth.healthInformation.aktivitet.periode.sortedFOMDate().first()
                infotrygdlastBlokk.forsteFravaersDag shouldBeEqualTo
                    ifth.healthInformation.aktivitet.periode.sortedFOMDate().first()
            }

            test("behandlingsDato skal være første fom hvis sykmelding har vært innom manuell") {
                val healthInformation =
                    HelseOpplysningerArbeidsuforhet().apply {
                        aktivitet =
                            HelseOpplysningerArbeidsuforhet.Aktivitet().apply {
                                periode.add(
                                    HelseOpplysningerArbeidsuforhet.Aktivitet.Periode().apply {
                                        periodeFOMDato = LocalDate.of(2019, 1, 1)
                                        periodeTOMDato = LocalDate.of(2019, 1, 2)
                                    },
                                )
                            }
                    }
                val infotrygdForesp =
                    InfotrygdForesp().apply {
                        sMhistorikk =
                            InfotrygdForesp.SMhistorikk().apply {
                                status = StatusType().apply { kodeMelding = "04" }
                            }
                    }
                val ifth = InfotrygdForespAndHealthInformation(infotrygdForesp, healthInformation)
                val perioder =
                    ifth.healthInformation.aktivitet.periode.sortedBy { it.periodeFOMDato }
                val operasjonstypeAndFom =
                    findoperasjonstypeAndFom(
                        healthInformation.aktivitet.periode.sortedFOMDate().first(),
                        healthInformation.aktivitet.periode.maxOf { it.periodeTOMDato },
                        infotrygdForesp.getInfotrygdPerioder()
                    )
                val forsteFravaersDag = operasjonstypeAndFom.second

                val infotrygdBlokk =
                    createInfotrygdBlokk(
                        ifth,
                        healthInformation.aktivitet.periode.last(),
                        "2134",
                        LocalDate.now(),
                        "LE",
                        "12345",
                        LoggingMeta("mottakId", "12315", "", ""),
                        "1234",
                        "NAV IKT",
                        behandletAvManuell = true,
                        utenlandskSykmelding = false,
                        operasjonstypeAndFom,
                    )

                infotrygdBlokk.behandlingsDato shouldBeEqualTo LocalDate.of(2019, 1, 1)
            }

            test("Setter riktig behandlerinformasjon for utenlandsk sykmelding") {
                val healthInformation =
                    HelseOpplysningerArbeidsuforhet().apply {
                        aktivitet =
                            HelseOpplysningerArbeidsuforhet.Aktivitet().apply {
                                periode.add(
                                    HelseOpplysningerArbeidsuforhet.Aktivitet.Periode().apply {
                                        periodeFOMDato = LocalDate.of(2019, 1, 1)
                                        periodeTOMDato = LocalDate.of(2019, 1, 2)
                                    },
                                )
                            }
                    }
                val infotrygdForesp =
                    InfotrygdForesp().apply {
                        sMhistorikk =
                            InfotrygdForesp.SMhistorikk().apply {
                                status = StatusType().apply { kodeMelding = "04" }
                            }
                    }
                val ifth = InfotrygdForespAndHealthInformation(infotrygdForesp, healthInformation)
                val perioder =
                    ifth.healthInformation.aktivitet.periode.sortedBy { it.periodeFOMDato }
                val operasjonstypeAndFom =
                    findoperasjonstypeAndFom(
                        healthInformation.aktivitet.periode.sortedFOMDate().first(),
                        healthInformation.aktivitet.periode.maxOf { it.periodeTOMDato },
                        infotrygdForesp.getInfotrygdPerioder()
                    )
                val forsteFravaersDag = operasjonstypeAndFom.second

                val infotrygdBlokk =
                    createInfotrygdBlokk(
                        ifth,
                        healthInformation.aktivitet.periode.last(),
                        "2134",
                        LocalDate.now(),
                        "LE",
                        "0",
                        LoggingMeta("mottakId", "12315", "", ""),
                        "1234",
                        "NAV IKT",
                        behandletAvManuell = false,
                        utenlandskSykmelding = true,
                        operasjonstypeAndFom,
                    )

                infotrygdBlokk.legeEllerInstitusjonsNummer shouldBeEqualTo
                    UTENLANDSK_SYKEHUS.toBigInteger()
                infotrygdBlokk.mottakerKode shouldBeEqualTo "IN"
            }
        }
    })
