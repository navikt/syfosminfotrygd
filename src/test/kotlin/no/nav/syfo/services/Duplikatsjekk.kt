package no.nav.syfo.services

import io.kotest.core.spec.style.FunSpec
import no.nav.helse.infotrygd.foresp.InfotrygdForesp
import no.nav.helse.infotrygd.foresp.StatusType
import no.nav.helse.infotrygd.foresp.TypeSMinfo
import no.nav.helse.sm2013.CS
import no.nav.helse.sm2013.HelseOpplysningerArbeidsuforhet
import no.nav.syfo.InfotrygdForespAndHealthInformation
import no.nav.syfo.services.updateinfotrygd.createInfotrygdBlokk
import no.nav.syfo.util.LoggingMeta
import org.amshove.kluent.shouldBeEqualTo
import java.time.LocalDate

class Duplikatsjekk : FunSpec({
    context("Tester duplikat hånderingen med redis") {
        test("Skal plukke meldingen som duplikat") {
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
                    sykmelding.add(
                        TypeSMinfo().apply {
                            periode = TypeSMinfo.Periode().apply {
                            }
                        }
                    )
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
                LocalDate.now(),
                false,
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
                LocalDate.now(),
                false,
                1

            )

            val sha256StringForsteMelding = sha256hashstring(infotrygdBlokkForsteMelding)
            val sha256StringAndreMelding = sha256hashstring(infotrygdBlokkAndreMelding)

            sha256StringForsteMelding shouldBeEqualTo sha256StringAndreMelding
        }
    }
})
