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
import no.nav.syfo.rules.sortedSMInfos
import no.nav.syfo.services.BehandlingsutfallService
import no.nav.syfo.services.UpdateInfotrygdService
import no.nav.syfo.util.LoggingMeta
import org.amshove.kluent.shouldBeEqualTo
import org.apache.kafka.clients.producer.KafkaProducer
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.LocalDate

object CreateInfotrygdBlokkSpek : Spek({
    describe("Testing the creating of infotrygdblokk") {
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
            val forsteFravaersDag = updateInfotrygdService.finnForsteFravaersDag(ifth, perioder.first(), LoggingMeta("mottakId", "12315", "", ""))

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

            infotrygdBlokk.forsteFravaersDag shouldBeEqualTo ifth.healthInformation.aktivitet.periode.sortedFOMDate().first()
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
                    sykmelding.add(
                        TypeSMinfo().apply {
                            periode = TypeSMinfo.Periode().apply {
                                arbufoerFOM = LocalDate.of(2019, 1, 1)
                                arbufoerTOM = LocalDate.of(2019, 1, 1)
                            }
                        }
                    )
                    status = StatusType().apply {
                        kodeMelding = "00"
                    }
                }
            }

            val ifth = InfotrygdForespAndHealthInformation(infotrygdForesp, healthInformation)

            val perioder = ifth.healthInformation.aktivitet.periode.sortedBy { it.periodeFOMDato }
            val forsteFravaersDag = updateInfotrygdService.finnForsteFravaersDag(ifth, perioder.first(), LoggingMeta("mottakId", "12315", "", ""))

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

            infotrygdfirstBlokk.forsteFravaersDag shouldBeEqualTo ifth.infotrygdForesp.sMhistorikk.sykmelding
                .sortedSMInfos()
                .last().periode.arbufoerFOM
            infotrygdlastBlokk.forsteFravaersDag shouldBeEqualTo ifth.infotrygdForesp.sMhistorikk.sykmelding
                .sortedSMInfos()
                .last().periode.arbufoerFOM
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
                    sykmelding.add(
                        TypeSMinfo().apply {
                            periode = TypeSMinfo.Periode().apply {
                                arbufoerFOM = LocalDate.of(2019, 1, 1)
                                arbufoerTOM = LocalDate.of(2019, 1, 1)
                            }
                        }
                    )
                    status = StatusType().apply {
                        kodeMelding = "00"
                    }
                }
            }

            val ifth = InfotrygdForespAndHealthInformation(infotrygdForesp, healthInformation)

            val perioder = ifth.healthInformation.aktivitet.periode.sortedBy { it.periodeFOMDato }
            val forsteFravaersDag = updateInfotrygdService.finnForsteFravaersDag(ifth, perioder.first(), LoggingMeta("mottakId", "12315", "", ""))

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

            infotrygdfirstBlokk.forsteFravaersDag shouldBeEqualTo ifth.healthInformation.aktivitet.periode.sortedFOMDate().first()
            infotrygdlastBlokk.forsteFravaersDag shouldBeEqualTo ifth.healthInformation.aktivitet.periode.sortedFOMDate().first()
        }
    }
})
