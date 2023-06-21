package no.nav.syfo.services

import io.kotest.core.spec.style.FunSpec
import no.nav.helse.sm2013.CV
import no.nav.helse.sm2013.HelseOpplysningerArbeidsuforhet
import no.nav.syfo.model.Status
import org.amshove.kluent.shouldBeEqualTo

class ValiderMottattSykmeldingSpek :
    FunSpec({
        context("Testing av metoden validerMottattSykmelding") {
            test("ValiderMottattSykmelding skal returnere OK hvis hoveddiagnose ikke er null") {
                val helseOpplysningerArbeidsuforhet =
                    HelseOpplysningerArbeidsuforhet().apply {
                        medisinskVurdering =
                            HelseOpplysningerArbeidsuforhet.MedisinskVurdering().apply {
                                hovedDiagnose =
                                    HelseOpplysningerArbeidsuforhet.MedisinskVurdering
                                        .HovedDiagnose()
                                        .apply { diagnosekode = CV() }
                            }
                    }

                validerMottattSykmelding(helseOpplysningerArbeidsuforhet).status shouldBeEqualTo
                    Status.OK
            }

            test(
                "ValiderMottattSykmelding skal returnere MANUAL_PROCESSING hvis hoveddiagnose er null"
            ) {
                val helseOpplysningerArbeidsuforhet =
                    HelseOpplysningerArbeidsuforhet().apply {
                        medisinskVurdering =
                            HelseOpplysningerArbeidsuforhet.MedisinskVurdering().apply {
                                hovedDiagnose = null
                            }
                    }

                validerMottattSykmelding(helseOpplysningerArbeidsuforhet).status shouldBeEqualTo
                    Status.MANUAL_PROCESSING
            }
        }
    })
