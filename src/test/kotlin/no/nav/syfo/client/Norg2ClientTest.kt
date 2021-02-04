package no.nav.syfo.client

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
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
import io.ktor.util.KtorExperimentalAPI
import kotlinx.coroutines.runBlocking
import no.nav.syfo.NAV_OPPFOLGING_UTLAND_KONTOR_NR
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.net.ServerSocket
import java.util.concurrent.TimeUnit

@KtorExperimentalAPI
object Norg2ClientTest : Spek({
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

    val norg2Client = Norg2Client(httpClient, "$mockHttpServerUrl/norg2")

    afterGroup {
        mockServer.stop(TimeUnit.SECONDS.toMillis(1), TimeUnit.SECONDS.toMillis(1))
    }

    describe("Norg2Client") {
        it("Returnerer riktig NAV-kontor") {
            runBlocking {
                norg2Client.getLocalNAVOffice("1411", null) shouldBeEqualTo Enhet("1400")
            }
        }
        it("Returnerer NAV Utland hvis vi ikke finner lokalkontor") {
            runBlocking {
                norg2Client.getLocalNAVOffice("POL", null) shouldBeEqualTo Enhet(NAV_OPPFOLGING_UTLAND_KONTOR_NR)
            }
        }
    }
})
