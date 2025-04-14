package no.nav.syfo.infotrygd

import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import java.time.LocalDate
import java.util.UUID
import no.nav.syfo.model.sykmelding.Diagnose

data class InfotrygdQuery(
    val ident: String,
    val fom: LocalDate?,
    val tom: LocalDate?,
    val hoveddiagnose: Diagnose?,
    val bidiagnose: Diagnose?,
    val tknumber: String?,
    val fodselsnrBehandler: String?,
    val traceId: String = UUID.randomUUID().toString(),
)

fun Route.registerInfotrygdApi(infotrygdService: InfotrygdService) {
    authenticate("servicebrukerAAD") {
        get("api/infotrygd") {
            val query = call.receive<InfotrygdQuery>()
            call.respond(infotrygdService.sendInfotrygdForesporsel(query))
        }
    }
}
