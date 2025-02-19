package no.nav.syfo.client.norg

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.kotest.core.spec.style.FunSpec
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.net.ServerSocket
import java.util.concurrent.TimeUnit
import no.nav.syfo.NAV_OPPFOLGING_UTLAND_KONTOR_NR
import no.nav.syfo.NAV_VIKAFOSSEN_KONTOR_NR
import no.nav.syfo.util.LoggingMeta
import org.amshove.kluent.shouldBeEqualTo

class Norg2ClientTest :
    FunSpec({
        val loggingMeta = LoggingMeta("mottakid", "orgnr", "msgid", "sykmeldingid")
        val norg2ValkeyService = mockk<Norg2ValkeyService>(relaxed = true)
        val httpClient =
            HttpClient(CIO) {
                install(ContentNegotiation) {
                    jackson {
                        registerKotlinModule()
                        registerModule(JavaTimeModule())
                        configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                        configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                    }
                }
                install(HttpRequestRetry) {
                    maxRetries = 3
                    delayMillis { retry -> retry * 50L }
                }
            }

        val mockHttpServerPort = ServerSocket(0).use { it.localPort }
        val mockHttpServerUrl = "http://localhost:$mockHttpServerPort"
        val mockServer =
            embeddedServer(Netty, mockHttpServerPort) {
                    install(io.ktor.server.plugins.contentnegotiation.ContentNegotiation) {
                        jackson {}
                    }
                    routing {
                        get("/norg2/enhet/navkontor/1411") { call.respond(Enhet("1400")) }
                        get("/norg2/enhet/navkontor/POL") { call.respond(HttpStatusCode.NotFound) }
                        get("/norg2/enhet/navkontor/2103") { call.respond(Enhet("2103")) }
                        get("/norg2/enhet/navkontor/null") {
                            when (call.parameters["disk"]) {
                                "SPSF" -> call.respond(Enhet("2103"))
                                else -> call.respond(Enhet("2101"))
                            }
                        }
                    }
                }
                .start()

        val norg2Client = Norg2Client(httpClient, "$mockHttpServerUrl/norg2", norg2ValkeyService)

        beforeTest {
            clearMocks(norg2ValkeyService)
            coEvery { norg2ValkeyService.getEnhet(any()) } returns null
        }

        afterSpec { mockServer.stop(TimeUnit.SECONDS.toMillis(1), TimeUnit.SECONDS.toMillis(1)) }

        context("Norg2Client") {
            test("Returnerer riktig NAV-kontor") {
                norg2Client.getLocalNAVOffice("1411", null, loggingMeta) shouldBeEqualTo
                    Enhet("1400")
            }
            test("Returnerer NAV Utland hvis vi ikke finner lokalkontor") {
                norg2Client.getLocalNAVOffice("POL", null, loggingMeta) shouldBeEqualTo
                    Enhet(NAV_OPPFOLGING_UTLAND_KONTOR_NR)
            }
            test("Oppdaterer valkey") {
                norg2Client.getLocalNAVOffice("1411", null, loggingMeta) shouldBeEqualTo
                    Enhet("1400")
                coVerify(exactly = 1) { norg2ValkeyService.putEnhet("1411", Enhet("1400")) }
            }
            test("Oppdaterer ikke valkey ved diskresjonskode") {
                norg2Client.getLocalNAVOffice("1411", "SPSF", loggingMeta) shouldBeEqualTo
                    Enhet("1400")

                coVerify(exactly = 0) { norg2ValkeyService.putEnhet(any(), any()) }
            }
            test("geografisk tilhørighet er null") {
                norg2Client.getLocalNAVOffice(null, null, loggingMeta) shouldBeEqualTo
                    Enhet(NAV_OPPFOLGING_UTLAND_KONTOR_NR)
                coVerify(exactly = 0) { norg2ValkeyService.putEnhet(any(), any()) }
            }
            test("geografisk tilhørighet er null med diskresjonskode") {
                norg2Client.getLocalNAVOffice(null, "SPSF", loggingMeta) shouldBeEqualTo
                    Enhet(NAV_VIKAFOSSEN_KONTOR_NR)
                coVerify(exactly = 0) { norg2ValkeyService.putEnhet(any(), any()) }
            }
        }
    })
