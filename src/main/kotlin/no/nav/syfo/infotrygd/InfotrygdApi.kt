package no.nav.syfo.infotrygd

import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import java.time.LocalDate
import java.util.UUID
import no.nav.syfo.model.sykmelding.Diagnose

data class InfotrygdQuery(
    val ident: String,
    val fom: LocalDate? = null,
    val tom: LocalDate? = null,
    val hoveddiagnose: Diagnose? = null,
    val bidiagnose: Diagnose? = null,
    val tknumber: String? = null,
    val fodselsnrBehandler: String? = null,
    val traceId: String = UUID.randomUUID().toString(),
)

data class InfotrygdUpdateRequest(
    val mottakId: String,
    val msgId: String,
    val sykmeldingId: String,
    val fnr: String,
    val fom: LocalDate,
    val tom: LocalDate,
    val toTom: LocalDate,
    val dryRun: Boolean = true,
    val navKontorNr: String?,
)

fun Route.registerInfotrygdApi(infotrygdService: InfotrygdService) {
    authenticate("servicebrukerAAD") {
        get("/api/infotrygd") {
            val query = call.receive<InfotrygdQuery>()
            call.respond(infotrygdService.sendInfotrygdForesporsel(query))
        }
        get("/api/infotrygd/details") {
            val query = call.receive<InfotrygdQuery>()
            call.respond(infotrygdService.sendInfotrygdForesporselWithDetails(query))
        }
        post("/api/infotrygd") {
            val updateRequest = call.receive<InfotrygdUpdateRequest>()
            val result = infotrygdService.tryUpdateInfotrygd(updateRequest)
            call.respond(result)
        }
    }
}
