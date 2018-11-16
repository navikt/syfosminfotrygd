package no.nav.syfo.api

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.engine.config
import io.ktor.client.features.auth.basic.BasicAuth
import io.ktor.client.features.json.GsonSerializer
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.request.accept
import io.ktor.client.request.post
import io.ktor.http.ContentType
import no.nav.syfo.Environment

fun createHttpClient(env: Environment) = HttpClient(CIO.config {
    maxConnectionsCount = 1000 // Maximum number of socket connections.
    endpoint.apply {
        maxConnectionsPerRoute = 100
        pipelineMaxSize = 20
        keepAliveTime = 5000
        connectTimeout = 5000
        connectRetryAttempts = 5
    }
}) {
    install(BasicAuth) {
        username = env.srvsminfotrygdUsername
        password = env.srvsminfotrygdPassword
    }
    install(JsonFeature) {
        serializer = GsonSerializer()
    }
}

suspend fun HttpClient.sendApprec(env: Environment, payload: String): Boolean = post {

    // TODO create the complete apprec with fellesformat here createApprec()
    body = payload
    accept(ContentType.Application.Json)

    url {
        host = env.syfoSmRegelerApiURL
        path("v1", "apprec")
    }
}
