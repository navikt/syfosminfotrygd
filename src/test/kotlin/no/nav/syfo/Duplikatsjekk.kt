package no.nav.syfo

import io.mockk.mockk
import no.nav.helse.infotrygd.foresp.InfotrygdForesp
import no.nav.helse.infotrygd.foresp.StatusType
import no.nav.helse.infotrygd.foresp.TypeSMinfo
import no.nav.helse.sm2013.CS
import no.nav.helse.sm2013.HelseOpplysningerArbeidsuforhet
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.client.ManuellClient
import no.nav.syfo.client.NorskHelsenettClient
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.sak.avro.ProduceTask
import no.nav.syfo.services.UpdateInfotrygdService
import no.nav.syfo.services.sha256hashstring
import no.nav.syfo.util.LoggingMeta
import org.amshove.kluent.shouldBeEqualTo
import org.apache.kafka.clients.producer.KafkaProducer
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.LocalDate

object Duplikatsjekk : Spek({

    val manuellClient = mockk<ManuellClient>()
    val norskHelsenettClient = mockk<NorskHelsenettClient>()
    val kafkaproducerCreateTask = mockk<KafkaProducer<String, ProduceTask>>()
    val kafkaproducerreceivedSykmelding = mockk<KafkaProducer<String, ReceivedSykmelding>>()
    val kafkaproducervalidationResult = mockk<KafkaProducer<String, ValidationResult>>()
    val updateInfotrygdService = UpdateInfotrygdService(
        manuellClient,
        norskHelsenettClient,
        kafkaproducerCreateTask,
        kafkaproducerreceivedSykmelding,
        "retry",
        "oppgave",
        kafkaproducervalidationResult,
        "behandlingsutfall",
        ApplicationState(alive = true, ready = true)
    )

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

            sha256StringForsteMelding shouldBeEqualTo sha256StringAndreMelding
        }
    }
})
