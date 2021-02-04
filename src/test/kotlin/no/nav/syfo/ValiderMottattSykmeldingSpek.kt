package no.nav.syfo

import io.ktor.util.KtorExperimentalAPI
import no.nav.helse.sm2013.CV
import no.nav.helse.sm2013.HelseOpplysningerArbeidsuforhet
import no.nav.syfo.model.Status
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

@KtorExperimentalAPI
object ValiderMottattSykmeldingSpek : Spek({

    describe("Testing av metoden validerMottattSykmelding") {
        it("ValiderMottattSykmelding skal returnere OK hvis hoveddiagnose ikke er null") {
            val helseOpplysningerArbeidsuforhet = HelseOpplysningerArbeidsuforhet().apply {
                medisinskVurdering = HelseOpplysningerArbeidsuforhet.MedisinskVurdering().apply {
                    hovedDiagnose = HelseOpplysningerArbeidsuforhet.MedisinskVurdering.HovedDiagnose().apply {
                        diagnosekode = CV()
                    }
                }
            }

            validerMottattSykmelding(helseOpplysningerArbeidsuforhet).status shouldBeEqualTo Status.OK
        }

        it("ValiderMottattSykmelding skal returnere MANUAL_PROCESSING hvis hoveddiagnose er null") {
            val helseOpplysningerArbeidsuforhet = HelseOpplysningerArbeidsuforhet().apply {
                medisinskVurdering = HelseOpplysningerArbeidsuforhet.MedisinskVurdering().apply {
                    hovedDiagnose = null
                }
            }

            validerMottattSykmelding(helseOpplysningerArbeidsuforhet).status shouldBeEqualTo Status.MANUAL_PROCESSING
        }
    }
})
