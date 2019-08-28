package no.nav.syfo

import java.time.LocalDate
import no.nav.helse.infotrygd.foresp.InfotrygdForesp
import no.nav.helse.infotrygd.foresp.StatusType
import no.nav.helse.infotrygd.foresp.TypeSMinfo
import no.nav.helse.sm2013.CS
import no.nav.helse.sm2013.HelseOpplysningerArbeidsuforhet
import org.amshove.kluent.shouldEqual
import org.amshove.kluent.shouldNotEqual
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object Duplikatsjekk : Spek({
    describe("Tester duplikat hånderingen med redis") {
        it("Skal plukke meldingen som duplikat") {
            val healthInformationForstemelding = HelseOpplysningerArbeidsuforhet().apply {
                aktivitet = HelseOpplysningerArbeidsuforhet.Aktivitet().apply {
                    periode.add(
                            HelseOpplysningerArbeidsuforhet.Aktivitet.Periode().apply {
                                periodeFOMDato = LocalDate.of(2019, 1, 1)
                                periodeTOMDato = LocalDate.of(2019, 1, 2)
                            }
                    )
                }
            }

            val infotrygdForesp = InfotrygdForesp().apply {
                sMhistorikk = InfotrygdForesp.SMhistorikk().apply {
                    sykmelding.add(TypeSMinfo().apply {
                        periode = TypeSMinfo.Periode().apply {
                        }
                    })
                    status = StatusType().apply {
                        kodeMelding = "00"
                    }
                }
            }

            val ifthForstemelding = InfotrygdForespAndHealthInformation(infotrygdForesp, healthInformationForstemelding)

            val infotrygdBlokkForsteMelding = createInfotrygdBlokk(
                    ifthForstemelding,
                    healthInformationForstemelding.aktivitet.periode.last(),
                    "2134",
                    LocalDate.now(),
                    "LE",
                    "12345",
                    LoggingMeta("mottakId", "12315", "", ""),
                    "1234",
                    "NAV IKT",
                    1

            )

            val healthInformationAndreMelding = HelseOpplysningerArbeidsuforhet().apply {
                aktivitet = HelseOpplysningerArbeidsuforhet.Aktivitet().apply {
                    periode.add(
                            HelseOpplysningerArbeidsuforhet.Aktivitet.Periode().apply {
                                periodeFOMDato = LocalDate.of(2019, 1, 1)
                                periodeTOMDato = LocalDate.of(2019, 1, 2)
                            }
                    )
                }
                arbeidsgiver = HelseOpplysningerArbeidsuforhet.Arbeidsgiver().apply {
                    navnArbeidsgiver = "Nav ikt"
                    harArbeidsgiver = CS().apply {
                        dn = "EN_ARBEIDSGIVER"
                        v = "1"
                    }
                }
            }

            val ifthAndreMelding = InfotrygdForespAndHealthInformation(infotrygdForesp, healthInformationAndreMelding)

            val infotrygdBlokkAndreMelding = createInfotrygdBlokk(
                    ifthAndreMelding,
                    healthInformationAndreMelding.aktivitet.periode.last(),
                    "2134",
                    LocalDate.now(),
                    "LE",
                    "12345",
                    LoggingMeta("mottakId", "12315", "2424", "2424"),
                    "1234",
                    "NAV IKT",
                    1

            )

            val sha256StringForsteMelding = sha256hashstring(infotrygdBlokkForsteMelding)
            val sha256StringAndreMelding = sha256hashstring(infotrygdBlokkAndreMelding)

            sha256StringForsteMelding shouldEqual sha256StringAndreMelding
        }

        it("Skal plukke meldingen som duplikat, andre melding med arbeidsgivernavn") {

            val healthInformationForstemelding = HelseOpplysningerArbeidsuforhet().apply {
                aktivitet = HelseOpplysningerArbeidsuforhet.Aktivitet().apply {
                    periode.add(
                            HelseOpplysningerArbeidsuforhet.Aktivitet.Periode().apply {
                                periodeFOMDato = LocalDate.of(2019, 1, 1)
                                periodeTOMDato = LocalDate.of(2019, 1, 2)
                            }
                    )
                    arbeidsgiver = HelseOpplysningerArbeidsuforhet.Arbeidsgiver().apply {
                        navnArbeidsgiver = null
                        harArbeidsgiver = CS().apply {
                            dn = "INGEN_ARBEIDSGIVER"
                            v = "3"
                        }
                    }
                }
            }

            val infotrygdForesp = InfotrygdForesp().apply {
                sMhistorikk = InfotrygdForesp.SMhistorikk().apply {
                    sykmelding.add(TypeSMinfo().apply {
                        periode = TypeSMinfo.Periode().apply {
                        }
                    })
                    status = StatusType().apply {
                        kodeMelding = "00"
                    }
                }
            }

            val ifthForstemelding = InfotrygdForespAndHealthInformation(infotrygdForesp, healthInformationForstemelding)

            val infotrygdBlokkForsteMelding = createInfotrygdBlokk(
                    ifthForstemelding,
                    healthInformationForstemelding.aktivitet.periode.first(),
                    "2134",
                    LocalDate.of(2019, 1, 1),
                    "LE",
                    "12345",
                    LoggingMeta("mottakId", "12315", "", ""),
                    "1234",
                    ifthForstemelding.healthInformation.arbeidsgiver?.navnArbeidsgiver,
                    1

            )

            val infotrygdUtenArbeidsgiverBlokkForsteMelding = createInfotrygdBlokk(
                    ifthForstemelding,
                    healthInformationForstemelding.aktivitet.periode.first(),
                    "2134",
                    LocalDate.of(2019, 1, 1),
                    "LE",
                    "12345",
                    LoggingMeta("mottakId", "12315", "", ""),
                    "1234",
                    "",
                    1

            )

            val healthInformationAndreMelding = HelseOpplysningerArbeidsuforhet().apply {
                aktivitet = HelseOpplysningerArbeidsuforhet.Aktivitet().apply {
                    periode.add(
                            HelseOpplysningerArbeidsuforhet.Aktivitet.Periode().apply {
                                periodeFOMDato = LocalDate.of(2019, 1, 1)
                                periodeTOMDato = LocalDate.of(2019, 1, 2)
                            }
                    )
                }
                arbeidsgiver = HelseOpplysningerArbeidsuforhet.Arbeidsgiver().apply {
                    navnArbeidsgiver = "Nav ikt"
                    harArbeidsgiver = CS().apply {
                        dn = "EN_ARBEIDSGIVER"
                        v = "1"
                    }
                }
            }

            val ifthAndreMelding = InfotrygdForespAndHealthInformation(infotrygdForesp, healthInformationAndreMelding)

            val infotrygdBlokkAndreMelding = createInfotrygdBlokk(
                    ifthAndreMelding,
                    healthInformationAndreMelding.aktivitet.periode.first(),
                    "2134",
                    LocalDate.of(2019, 1, 1),
                    "LE",
                    "12345",
                    LoggingMeta("mottakId", "12315", "", ""),
                    "1234",
                    ifthAndreMelding.healthInformation.arbeidsgiver?.navnArbeidsgiver,
                    1

            )

            val infotrygdUtenArbeidsgiverBlokkAndreMelding = createInfotrygdBlokk(
                    ifthAndreMelding,
                    healthInformationAndreMelding.aktivitet.periode.first(),
                    "2134",
                    LocalDate.of(2019, 1, 1),
                    "LE",
                    "12345",
                    LoggingMeta("mottakId", "12315", "", ""),
                    "1234",
                    "",
                    1

            )

            val sha256StringForsteMelding = sha256hashstring(infotrygdBlokkForsteMelding)
            val sha256StringAndreMelding = sha256hashstring(infotrygdBlokkAndreMelding)

            val sha256StringUtenArbeidsgiverAndreMelding = sha256hashstring(infotrygdUtenArbeidsgiverBlokkForsteMelding)
            val sha256StringUtenArbeidsgiverForsteMelding = sha256hashstring(infotrygdUtenArbeidsgiverBlokkAndreMelding)

            println(infotrygdBlokkForsteMelding.arbeidsKategori)
            println(infotrygdBlokkAndreMelding.arbeidsKategori)

            sha256StringForsteMelding shouldNotEqual sha256StringAndreMelding
            sha256StringUtenArbeidsgiverForsteMelding shouldEqual sha256StringUtenArbeidsgiverAndreMelding
        }

        it("Skal plukke meldingen som duplikat, forste melding med arbeidsgivernavn") {

            val healthInformationForstemelding = HelseOpplysningerArbeidsuforhet().apply {
                aktivitet = HelseOpplysningerArbeidsuforhet.Aktivitet().apply {
                    periode.add(
                            HelseOpplysningerArbeidsuforhet.Aktivitet.Periode().apply {
                                periodeFOMDato = LocalDate.of(2019, 1, 1)
                                periodeTOMDato = LocalDate.of(2019, 1, 2)
                            }
                    )
                }
                arbeidsgiver = HelseOpplysningerArbeidsuforhet.Arbeidsgiver().apply {
                    navnArbeidsgiver = "Nav ikt"
                    harArbeidsgiver = CS().apply {
                        dn = "EN_ARBEIDSGIVER"
                        v = "1"
                    }
                }
            }

            val infotrygdForesp = InfotrygdForesp().apply {
                sMhistorikk = InfotrygdForesp.SMhistorikk().apply {
                    sykmelding.add(TypeSMinfo().apply {
                        periode = TypeSMinfo.Periode().apply {
                        }
                    })
                    status = StatusType().apply {
                        kodeMelding = "00"
                    }
                }
            }

            val ifthForstemelding = InfotrygdForespAndHealthInformation(infotrygdForesp, healthInformationForstemelding)

            val infotrygdBlokkForsteMelding = createInfotrygdBlokk(
                    ifthForstemelding,
                    healthInformationForstemelding.aktivitet.periode.first(),
                    "2134",
                    LocalDate.of(2019, 1, 1),
                    "LE",
                    "12345",
                    LoggingMeta("mottakId", "12315", "", ""),
                    "1234",
                    ifthForstemelding.healthInformation.arbeidsgiver?.navnArbeidsgiver,
                    1

            )

            val infotrygdUtenArbeidsgiverBlokkForsteMelding = createInfotrygdBlokk(
                    ifthForstemelding,
                    healthInformationForstemelding.aktivitet.periode.first(),
                    "2134",
                    LocalDate.of(2019, 1, 1),
                    "LE",
                    "12345",
                    LoggingMeta("mottakId", "12315", "", ""),
                    "1234",
                    "",
                    1

            )

            val healthInformationAndreMelding = HelseOpplysningerArbeidsuforhet().apply {
                aktivitet = HelseOpplysningerArbeidsuforhet.Aktivitet().apply {
                    periode.add(
                            HelseOpplysningerArbeidsuforhet.Aktivitet.Periode().apply {
                                periodeFOMDato = LocalDate.of(2019, 1, 1)
                                periodeTOMDato = LocalDate.of(2019, 1, 2)
                            }
                    )
                }
            }

            val ifthAndreMelding = InfotrygdForespAndHealthInformation(infotrygdForesp, healthInformationAndreMelding)

            val infotrygdBlokkAndreMelding = createInfotrygdBlokk(
                    ifthAndreMelding,
                    healthInformationAndreMelding.aktivitet.periode.first(),
                    "2134",
                    LocalDate.of(2019, 1, 1),
                    "LE",
                    "12345",
                    LoggingMeta("mottakId", "12315", "2424", "2424"),
                    "1234",
                    ifthAndreMelding.healthInformation.arbeidsgiver?.navnArbeidsgiver,
                    1

            )

            val infotrygdUtenArbeidsgiverBlokkAndreMelding = createInfotrygdBlokk(
                    ifthAndreMelding,
                    healthInformationAndreMelding.aktivitet.periode.first(),
                    "2134",
                    LocalDate.of(2019, 1, 1),
                    "LE",
                    "12345",
                    LoggingMeta("mottakId", "12315", "2424", "2424"),
                    "1234",
                    "",
                    1

            )

            val sha256StringForsteMelding = sha256hashstring(infotrygdBlokkForsteMelding)
            val sha256StringAndreMelding = sha256hashstring(infotrygdBlokkAndreMelding)

            val sha256StringUtenArbeidsgiverAndreMelding = sha256hashstring(infotrygdUtenArbeidsgiverBlokkForsteMelding)
            val sha256StringUtenArbeidsgiverForsteMelding = sha256hashstring(infotrygdUtenArbeidsgiverBlokkAndreMelding)

                sha256StringForsteMelding shouldNotEqual sha256StringAndreMelding
                sha256StringUtenArbeidsgiverForsteMelding shouldEqual sha256StringUtenArbeidsgiverAndreMelding
        }
    }
})
