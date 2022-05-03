package no.nav.syfo

import io.kotest.core.spec.style.FunSpec
import io.mockk.mockk
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.client.Behandler
import no.nav.syfo.client.Godkjenning
import no.nav.syfo.client.Kode
import no.nav.syfo.client.ManuellClient
import no.nav.syfo.client.NorskHelsenettClient
import no.nav.syfo.model.HelsepersonellKategori
import no.nav.syfo.model.OpprettOppgaveKafkaMessage
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.services.BehandlingsutfallService
import no.nav.syfo.services.RedisService
import no.nav.syfo.services.UpdateInfotrygdService
import org.amshove.kluent.shouldBeEqualTo
import org.apache.kafka.clients.producer.KafkaProducer

class FinnAktivHelsepersonellAutorisasjonSpek : FunSpec({

    context("Tester at man finner riktig helsepersonell autorisasjoner verdi") {
        val manuellClient = mockk<ManuellClient>()
        val norskHelsenettClient = mockk<NorskHelsenettClient>()
        val kafkaAivenProducerReceivedSykmelding = mockk<KafkaProducer<String, ReceivedSykmelding>>()
        val kafkaAivenProducerOppgave = mockk<KafkaProducer<String, OpprettOppgaveKafkaMessage>>()
        val behandlingsutfallService = mockk<BehandlingsutfallService>()
        val redisService = mockk<RedisService>()
        val updateInfotrygdService = UpdateInfotrygdService(
            manuellClient,
            norskHelsenettClient,
            ApplicationState(alive = true, ready = true),
            kafkaAivenProducerReceivedSykmelding,
            kafkaAivenProducerOppgave,
            "retry",
            "oppgave",
            behandlingsutfallService,
            redisService
        )
        test("Sjekker at man velger Lege verdien dersom fleire helsepersonell autorisasjoner") {

            val helsepersonelPerson = Behandler(
                listOf(
                    Godkjenning(
                        autorisasjon = Kode(
                            aktiv = true,
                            oid = 0,
                            verdi = ""
                        ),
                        helsepersonellkategori = Kode(
                            aktiv = true,
                            oid = 0,
                            verdi = HelsepersonellKategori.KIROPRAKTOR.verdi
                        )
                    ),
                    Godkjenning(
                        autorisasjon = Kode(
                            aktiv = true,
                            oid = 0,
                            verdi = ""
                        ),
                        helsepersonellkategori = Kode(
                            aktiv = true,
                            oid = 0,
                            verdi = HelsepersonellKategori.LEGE.verdi
                        )
                    )

                )
            )

            updateInfotrygdService.finnAktivHelsepersonellAutorisasjons(helsepersonelPerson) shouldBeEqualTo HelsepersonellKategori.LEGE.verdi
        }

        test("Sjekker at man velger Kiropraktor verdien dersom dei andre helsepersonell autorisasjoner er inaktiv") {

            val helsepersonelPerson = Behandler(
                listOf(
                    Godkjenning(
                        autorisasjon = Kode(
                            aktiv = true,
                            oid = 0,
                            verdi = ""
                        ),
                        helsepersonellkategori = Kode(
                            aktiv = true,
                            oid = 0,
                            verdi = HelsepersonellKategori.KIROPRAKTOR.verdi
                        )
                    ),
                    Godkjenning(
                        autorisasjon = Kode(
                            aktiv = true,
                            oid = 0,
                            verdi = ""
                        ),
                        helsepersonellkategori = Kode(
                            aktiv = false,
                            oid = 0,
                            verdi = HelsepersonellKategori.LEGE.verdi
                        )
                    )

                )
            )

            updateInfotrygdService.finnAktivHelsepersonellAutorisasjons(helsepersonelPerson) shouldBeEqualTo HelsepersonellKategori.KIROPRAKTOR.verdi
        }

        test("Sjekker at man velger tomt verdi dersom ingen er aktive helsepersonellkategori verdier") {

            val helsepersonelPerson = Behandler(
                listOf(
                    Godkjenning(
                        autorisasjon = Kode(
                            aktiv = true,
                            oid = 0,
                            verdi = ""
                        ),
                        helsepersonellkategori = Kode(
                            aktiv = false,
                            oid = 0,
                            verdi = HelsepersonellKategori.KIROPRAKTOR.verdi
                        )
                    ),
                    Godkjenning(
                        autorisasjon = Kode(
                            aktiv = true,
                            oid = 0,
                            verdi = ""
                        ),
                        helsepersonellkategori = Kode(
                            aktiv = false,
                            oid = 0,
                            verdi = HelsepersonellKategori.LEGE.verdi
                        )
                    )

                )
            )

            updateInfotrygdService.finnAktivHelsepersonellAutorisasjons(helsepersonelPerson) shouldBeEqualTo ""
        }

        test("Sjekker at man velger tomt verdi dersom det er ingen godkjenninger") {

            val helsepersonelPerson = Behandler(emptyList())

            updateInfotrygdService.finnAktivHelsepersonellAutorisasjons(helsepersonelPerson) shouldBeEqualTo ""
        }
    }
})
