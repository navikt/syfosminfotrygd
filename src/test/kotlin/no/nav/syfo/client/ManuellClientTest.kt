package no.nav.syfo.client

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
import io.mockk.coEvery
import io.mockk.mockk
import no.nav.syfo.util.LoggingMeta
import org.amshove.kluent.shouldBeEqualTo
import java.net.ServerSocket
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.test.assertFailsWith

class ManuellClientTest : FunSpec({
    val loggingMeta = LoggingMeta("mottakid", "orgnr", "msgid", "sykmeldingid")
    val sykmeldingErBehandlet = UUID.randomUUID().toString()
    val sykmeldingErIkkeBehandlet = UUID.randomUUID().toString()
    val sykmeldingFeiler = UUID.randomUUID().toString()
    val accessTokenClient = mockk<AccessTokenClientV2>()
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
            get("/manuell//api/v1/sykmelding/$sykmeldingErBehandlet") {
                call.respond(HttpStatusCode.OK)
            }
            get("/manuell//api/v1/sykmelding/$sykmeldingErIkkeBehandlet") {
                call.respond(HttpStatusCode.NotFound)
            }
            get("/manuell//api/v1/sykmelding/$sykmeldingFeiler") {
                call.respond(HttpStatusCode.InternalServerError)
            }
        }
    }.start()

    val manuellClient = ManuellClient(httpClient, "$mockHttpServerUrl/manuell", accessTokenClient, "resource")

    beforeTest {
        coEvery { accessTokenClient.getAccessTokenV2(any()) } returns "token"
    }

    afterSpec {
        mockServer.stop(TimeUnit.SECONDS.toMillis(1), TimeUnit.SECONDS.toMillis(1))
    }

    context("ManuellClient") {
        test("Returnerer true hvis sykmeldingId er behandlet av manuell") {
            manuellClient.behandletAvManuell(sykmeldingErBehandlet, loggingMeta) shouldBeEqualTo true
        }
        test("Returnerer false hvis sykmeldingId ikke er behandlet av manuell") {
            manuellClient.behandletAvManuell(sykmeldingErIkkeBehandlet, loggingMeta) shouldBeEqualTo false
        }
        test("Feiler hvis kall mot manuell ikke gir OK eller NotFound") {
            assertFailsWith<RuntimeException> {
                manuellClient.behandletAvManuell(sykmeldingFeiler, loggingMeta)
            }
        }
    }
})
