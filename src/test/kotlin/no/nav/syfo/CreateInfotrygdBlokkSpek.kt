package no.nav.syfo

import io.ktor.util.KtorExperimentalAPI
import java.time.LocalDate
import no.nav.helse.infotrygd.foresp.InfotrygdForesp
import no.nav.helse.infotrygd.foresp.StatusType
import no.nav.helse.infotrygd.foresp.TypeSMinfo
import no.nav.helse.sm2013.HelseOpplysningerArbeidsuforhet
import no.nav.syfo.rules.sortedSMInfos
import no.nav.syfo.services.UpdateInfotrygdService
import org.amshove.kluent.shouldEqual
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

@KtorExperimentalAPI
object CreateInfotrygdBlokkSpek : Spek({
    describe("Testing the creating of infotrygdblokk") {

        val updateInfotrygdService = UpdateInfotrygdService()

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
                    status = StatusType().apply {
                        kodeMelding = "04"
                    }
                }
            }

            val ifth = InfotrygdForespAndHealthInformation(infotrygdForesp, healthInformation)

            val perioder = ifth.healthInformation.aktivitet.periode.sortedBy { it.periodeFOMDato }
            val typeSMinfo = ifth.infotrygdForesp.sMhistorikk?.sykmelding
                    ?.sortedSMInfos()
                    ?.lastOrNull()
            val forsteFravaersDag = if (UpdateInfotrygdService().findOperasjonstype(perioder.first(), ifth,
                            LoggingMeta("mottakId", "12315", "2424", "2424")) == 1) {
                ifth.healthInformation.aktivitet.periode.sortedFOMDate().first()
            } else {
                typeSMinfo?.periode?.arbufoerFOM ?: throw RuntimeException("Unable to find første fraværsdag in IT")
            }

            val infotrygdBlokk = updateInfotrygdService.createInfotrygdBlokk(
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
                    1

            )

            infotrygdBlokk.forsteFravaersDag shouldEqual ifth.healthInformation.aktivitet.periode.sortedFOMDate().first()
        }

        it("Should set forsteFravaersDag correctly, when oprasjosntype 2 and more than 1 periode") {

            val healthInformation = HelseOpplysningerArbeidsuforhet().apply {
                aktivitet = HelseOpplysningerArbeidsuforhet.Aktivitet().apply {
                    periode.add(
                            HelseOpplysningerArbeidsuforhet.Aktivitet.Periode().apply {
                                periodeFOMDato = LocalDate.of(2019, 1, 2)
                                periodeTOMDato = LocalDate.of(2019, 1, 3)
                            }
                    )
                    periode.add(
                            HelseOpplysningerArbeidsuforhet.Aktivitet.Periode().apply {
                                periodeFOMDato = LocalDate.of(2019, 1, 4)
                                periodeTOMDato = LocalDate.of(2019, 1, 5)
                            }
                    )
                }
            }

            val infotrygdForesp = InfotrygdForesp().apply {
                sMhistorikk = InfotrygdForesp.SMhistorikk().apply {
                        sykmelding.add(TypeSMinfo().apply {
                            periode = TypeSMinfo.Periode().apply {
                                arbufoerFOM = LocalDate.of(2019, 1, 1)
                                arbufoerTOM = LocalDate.of(2019, 1, 1)
                            }
                        })
                    status = StatusType().apply {
                        kodeMelding = "00"
                    }
                }
            }

            val ifth = InfotrygdForespAndHealthInformation(infotrygdForesp, healthInformation)

            val perioder = ifth.healthInformation.aktivitet.periode.sortedBy { it.periodeFOMDato }
            val typeSMinfo = ifth.infotrygdForesp.sMhistorikk?.sykmelding
                    ?.sortedSMInfos()
                    ?.lastOrNull()
            val forsteFravaersDag = if (UpdateInfotrygdService().findOperasjonstype(perioder.first(), ifth,
                            LoggingMeta("mottakId", "12315", "2424", "2424")) == 1) {
                ifth.healthInformation.aktivitet.periode.sortedFOMDate().first()
            } else {
                typeSMinfo?.periode?.arbufoerFOM ?: throw RuntimeException("Unable to find første fraværsdag in IT")
            }

            val infotrygdfirstBlokk = updateInfotrygdService.createInfotrygdBlokk(
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
                    2

            )

            val infotrygdlastBlokk = updateInfotrygdService.createInfotrygdBlokk(
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
                    2

            )

            infotrygdfirstBlokk.forsteFravaersDag shouldEqual typeSMinfo?.periode?.arbufoerFOM
            infotrygdlastBlokk.forsteFravaersDag shouldEqual typeSMinfo?.periode?.arbufoerFOM
        }

        it("Should set forsteFravaersDag correctly, when oprasjosntype 1 and more than 1 periode") {

            val healthInformation = HelseOpplysningerArbeidsuforhet().apply {
                aktivitet = HelseOpplysningerArbeidsuforhet.Aktivitet().apply {
                    periode.add(
                            HelseOpplysningerArbeidsuforhet.Aktivitet.Periode().apply {
                                periodeFOMDato = LocalDate.of(2019, 1, 3)
                                periodeTOMDato = LocalDate.of(2019, 1, 4)
                            }
                    )
                    periode.add(
                            HelseOpplysningerArbeidsuforhet.Aktivitet.Periode().apply {
                                periodeFOMDato = LocalDate.of(2019, 1, 5)
                                periodeTOMDato = LocalDate.of(2019, 1, 6)
                            }
                    )
                }
            }

            val infotrygdForesp = InfotrygdForesp().apply {
                sMhistorikk = InfotrygdForesp.SMhistorikk().apply {
                    sykmelding.add(TypeSMinfo().apply {
                        periode = TypeSMinfo.Periode().apply {
                            arbufoerFOM = LocalDate.of(2019, 1, 1)
                            arbufoerTOM = LocalDate.of(2019, 1, 1)
                        }
                    })
                    status = StatusType().apply {
                        kodeMelding = "00"
                    }
                }
            }

            val ifth = InfotrygdForespAndHealthInformation(infotrygdForesp, healthInformation)

            val perioder = ifth.healthInformation.aktivitet.periode.sortedBy { it.periodeFOMDato }
            val typeSMinfo = ifth.infotrygdForesp.sMhistorikk?.sykmelding
                    ?.sortedSMInfos()
                    ?.lastOrNull()
            val forsteFravaersDag = if (UpdateInfotrygdService().findOperasjonstype(perioder.first(), ifth,
                            LoggingMeta("mottakId", "12315", "2424", "2424")) == 1) {
                ifth.healthInformation.aktivitet.periode.sortedFOMDate().first()
            } else {
                typeSMinfo?.periode?.arbufoerFOM ?: throw RuntimeException("Unable to find første fraværsdag in IT")
            }

            val infotrygdfirstBlokk = updateInfotrygdService.createInfotrygdBlokk(
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
                    1

            )

            val infotrygdlastBlokk = updateInfotrygdService.createInfotrygdBlokk(
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
                    2

            )

            infotrygdfirstBlokk.forsteFravaersDag shouldEqual ifth.healthInformation.aktivitet.periode.sortedFOMDate().first()
            infotrygdlastBlokk.forsteFravaersDag shouldEqual ifth.healthInformation.aktivitet.periode.sortedFOMDate().first()
        }
    }
})
