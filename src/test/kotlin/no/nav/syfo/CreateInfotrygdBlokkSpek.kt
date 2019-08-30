package no.nav.syfo

import java.time.LocalDate
import no.nav.helse.infotrygd.foresp.InfotrygdForesp
import no.nav.helse.infotrygd.foresp.StatusType
import no.nav.helse.infotrygd.foresp.TypeSMinfo
import no.nav.helse.sm2013.HelseOpplysningerArbeidsuforhet
import org.amshove.kluent.shouldEqual
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object CreateInfotrygdBlokkSpek : Spek({
    describe("Testing the creating of infotrygdblokk") {

        it("Should set forsteFravaersDag correctly, when oprasjosntype 1") {

            val healthInformation = HelseOpplysningerArbeidsuforhet().apply {
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

            val ifth = InfotrygdForespAndHealthInformation(infotrygdForesp, healthInformation)

            val infotrygdBlokk = createInfotrygdBlokk(
                    ifth,
                    healthInformation.aktivitet.periode.last(),
                    "2134",
                    LocalDate.now(),
                    "LE",
                    "12345",
                    LoggingMeta("mottakId", "12315", "", "", false),
                    "1234",
                    "NAV IKT",
                    1

            )

            infotrygdBlokk.forsteFravaersDag shouldEqual LocalDate.of(2019, 1, 1)
        }

        it("Should set forsteFravaersDag correctly, when oprasjosntype 2 and more than 1 periode") {

            val healthInformation = HelseOpplysningerArbeidsuforhet().apply {
                aktivitet = HelseOpplysningerArbeidsuforhet.Aktivitet().apply {
                    periode.add(
                            HelseOpplysningerArbeidsuforhet.Aktivitet.Periode().apply {
                                periodeFOMDato = LocalDate.of(2019, 1, 1)
                                periodeTOMDato = LocalDate.of(2019, 1, 2)
                            }
                    )
                    periode.add(
                            HelseOpplysningerArbeidsuforhet.Aktivitet.Periode().apply {
                                periodeFOMDato = LocalDate.of(2019, 1, 3)
                                periodeTOMDato = LocalDate.of(2019, 1, 4)
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

            val ifth = InfotrygdForespAndHealthInformation(infotrygdForesp, healthInformation)

            val infotrygdBlokk = createInfotrygdBlokk(
                    ifth,
                    healthInformation.aktivitet.periode.last(),
                    "2134",
                    LocalDate.now(),
                    "LE",
                    "12345",
                    LoggingMeta("mottakId", "12315", "", "", false),
                    "1234",
                    "NAV IKT",
                    2

            )

            infotrygdBlokk.forsteFravaersDag shouldEqual LocalDate.of(2019, 1, 1)
        }
    }
})
