package no.nav.syfo.client.norg

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.kotest.core.spec.style.FunSpec
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.client.HttpClient
import io.ktor.client.engine.apache.Apache
import io.ktor.client.features.json.JacksonSerializer
import io.ktor.client.features.json.JsonFeature
import io.ktor.features.ContentNegotiation
import io.ktor.http.HttpStatusCode
import io.ktor.jackson.jackson
import io.ktor.response.respond
import io.ktor.routing.get
import io.ktor.routing.routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import no.nav.syfo.NAV_OPPFOLGING_UTLAND_KONTOR_NR
import no.nav.syfo.util.LoggingMeta
import org.amshove.kluent.shouldBeEqualTo
import java.net.ServerSocket
import java.util.concurrent.TimeUnit

class Norg2ClientTest : FunSpec({
    val loggingMeta = LoggingMeta("mottakid", "orgnr", "msgid", "sykmeldingid")
    val norg2RedisService = mockk<Norg2RedisService>(relaxed = true)
    val httpClient = HttpClient(Apache) {
        install(JsonFeature) {
            serializer = JacksonSerializer {
                registerKotlinModule()
                registerModule(JavaTimeModule())
                configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            }
        }
    }

    val mockHttpServerPort = ServerSocket(0).use { it.localPort }
    val mockHttpServerUrl = "http://localhost:$mockHttpServerPort"
    val mockServer = embeddedServer(Netty, mockHttpServerPort) {
        install(ContentNegotiation) {
            jackson {}
        }
        routing {
            get("/norg2/enhet/navkontor/1411") {
                call.respond(Enhet("1400"))
            }
            get("/norg2/enhet/navkontor/POL") {
                call.respond(HttpStatusCode.NotFound)
            }
        }
    }.start()

    val norg2Client = Norg2Client(httpClient, "$mockHttpServerUrl/norg2", norg2RedisService)

    beforeTest {
        clearMocks(norg2RedisService)
        coEvery { norg2RedisService.getEnhet(any()) } returns null
    }

    afterSpec {
        mockServer.stop(TimeUnit.SECONDS.toMillis(1), TimeUnit.SECONDS.toMillis(1))
    }

    context("Norg2Client") {
        test("Returnerer riktig NAV-kontor") {
            norg2Client.getLocalNAVOffice("1411", null, loggingMeta) shouldBeEqualTo Enhet("1400")
        }
        test("Returnerer NAV Utland hvis vi ikke finner lokalkontor") {
            norg2Client.getLocalNAVOffice("POL", null, loggingMeta) shouldBeEqualTo Enhet(NAV_OPPFOLGING_UTLAND_KONTOR_NR)
        }
        test("Oppdaterer redis") {
            norg2Client.getLocalNAVOffice("1411", null, loggingMeta) shouldBeEqualTo Enhet("1400")

            coVerify(exactly = 1) { norg2RedisService.putEnhet("1411", Enhet("1400")) }
        }
        test("Oppdaterer ikke redis ved diskresjonskode") {
            norg2Client.getLocalNAVOffice("1411", "SPSF", loggingMeta) shouldBeEqualTo Enhet("1400")

            coVerify(exactly = 0) { norg2RedisService.putEnhet(any(), any()) }
        }
    }
})
