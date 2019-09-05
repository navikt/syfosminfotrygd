package no.nav.syfo

import io.ktor.util.KtorExperimentalAPI
import java.time.LocalDate
import no.nav.helse.infotrygd.foresp.InfotrygdForesp
import no.nav.helse.infotrygd.foresp.StatusType
import no.nav.helse.infotrygd.foresp.TypeSMinfo
import no.nav.helse.sm2013.CS
import no.nav.helse.sm2013.HelseOpplysningerArbeidsuforhet
import no.nav.syfo.services.UpdateInfotrygdService
import no.nav.syfo.services.sha256hashstring
import org.amshove.kluent.shouldEqual
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

@KtorExperimentalAPI
object Duplikatsjekk : Spek({

    val updateInfotrygdService = UpdateInfotrygdService()

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

            val infotrygdBlokkForsteMelding = updateInfotrygdService.createInfotrygdBlokk(
                    ifthForstemelding,
                    healthInformationForstemelding.aktivitet.periode.last(),
                    "2134",
                    LocalDate.now(),
                    "LE",
                    "12345",
                    LoggingMeta("mottakId", "12315", "", ""),
                    "1234",
                    "NAV IKT",
                    LocalDate.now(),
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

            val infotrygdBlokkAndreMelding = updateInfotrygdService.createInfotrygdBlokk(
                    ifthAndreMelding,
                    healthInformationAndreMelding.aktivitet.periode.last(),
                    "2134",
                    LocalDate.now(),
                    "LE",
                    "12345",
                    LoggingMeta("mottakId", "12315", "2424", "2424"),
                    "1234",
                    "NAV IKT",
                    LocalDate.now(),
                    1

            )

            val sha256StringForsteMelding = sha256hashstring(infotrygdBlokkForsteMelding)
            val sha256StringAndreMelding = sha256hashstring(infotrygdBlokkAndreMelding)

            sha256StringForsteMelding shouldEqual sha256StringAndreMelding
        }
    }
})
