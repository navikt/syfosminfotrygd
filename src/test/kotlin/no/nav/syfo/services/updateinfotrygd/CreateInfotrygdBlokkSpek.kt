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
                val forsteFravaersDag =
                    finnForsteFravaersDag(
                        ifth,
                        perioder.first(),
                        LoggingMeta("mottakId", "12315", "", "")
                    )

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
                        forsteFravaersDag,
                        behandletAvManuell = false,
                        utenlandskSykmelding = false,
                        operasjonstypeKode = 1,
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
                val forsteFravaersDag =
                    finnForsteFravaersDag(
                        ifth,
                        perioder.first(),
                        LoggingMeta("mottakId", "12315", "", "")
                    )

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
                        forsteFravaersDag,
                        behandletAvManuell = false,
                        utenlandskSykmelding = false,
                        operasjonstypeKode = 2,
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
                        forsteFravaersDag,
                        behandletAvManuell = false,
                        utenlandskSykmelding = false,
                        operasjonstypeKode = 2,
                    )

                infotrygdfirstBlokk.forsteFravaersDag shouldBeEqualTo
                    ifth.infotrygdForesp.sMhistorikk.sykmelding
                        .sortedSMInfos()
                        .last()
                        .periode
                        .arbufoerFOM
                infotrygdlastBlokk.forsteFravaersDag shouldBeEqualTo
                    ifth.infotrygdForesp.sMhistorikk.sykmelding
                        .sortedSMInfos()
                        .last()
                        .periode
                        .arbufoerFOM
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
                val forsteFravaersDag =
                    finnForsteFravaersDag(
                        ifth,
                        perioder.first(),
                        LoggingMeta("mottakId", "12315", "", "")
                    )

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
                        forsteFravaersDag,
                        behandletAvManuell = false,
                        utenlandskSykmelding = false,
                        operasjonstypeKode = 1,
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
                        forsteFravaersDag,
                        behandletAvManuell = false,
                        utenlandskSykmelding = false,
                        operasjonstypeKode = 2,
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
                val forsteFravaersDag =
                    finnForsteFravaersDag(
                        ifth,
                        perioder.first(),
                        LoggingMeta("mottakId", "12315", "", "")
                    )

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
                        forsteFravaersDag,
                        behandletAvManuell = true,
                        utenlandskSykmelding = false,
                        operasjonstypeKode = 1,
                    )

                infotrygdBlokk.behandlingsDato shouldBeEqualTo LocalDate.of(2019, 1, 1)
            }


        }
    })
