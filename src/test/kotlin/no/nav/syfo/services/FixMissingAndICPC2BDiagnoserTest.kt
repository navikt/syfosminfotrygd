package no.nav.syfo.services

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import no.nav.helse.sm2013.ArsakType
import no.nav.helse.sm2013.CV
import no.nav.helse.sm2013.HelseOpplysningerArbeidsuforhet
import no.nav.tsm.diagnoser.ICD10
import no.nav.tsm.diagnoser.ICPC2
import no.nav.tsm.diagnoser.ICPC2B

class FixMissingAndICPC2BDiagnoserTest :
    FunSpec({
        test("Should convert ICPC2B hoveddiagnose to ICPC2") {
            val helseOpplysningerArbeidsuforhet =
                HelseOpplysningerArbeidsuforhet().apply {
                    medisinskVurdering =
                        HelseOpplysningerArbeidsuforhet.MedisinskVurdering().apply {
                            hovedDiagnose =
                                HelseOpplysningerArbeidsuforhet.MedisinskVurdering.HovedDiagnose()
                                    .apply {
                                        diagnosekode =
                                            CV().apply {
                                                s = ICPC2B.OID
                                                v = "R74.0001"
                                                dn = "Øvre luftveisinfeksjon"
                                            }
                                    }
                        }
                }

            val result = fixMissingAndICPC2BDiagnoser(helseOpplysningerArbeidsuforhet)

            result.medisinskVurdering.hovedDiagnose.diagnosekode.s shouldBe ICPC2.OID
            result.medisinskVurdering.hovedDiagnose.diagnosekode.v shouldBe "R74"
            result.medisinskVurdering.hovedDiagnose.diagnosekode.dn shouldBe
                "Akutt øvre luftveisinfeksjon"
        }

        test("Should NOT convert regular ICPC2 hoveddiagnose") {
            val helseOpplysningerArbeidsuforhet =
                HelseOpplysningerArbeidsuforhet().apply {
                    medisinskVurdering =
                        HelseOpplysningerArbeidsuforhet.MedisinskVurdering().apply {
                            hovedDiagnose =
                                HelseOpplysningerArbeidsuforhet.MedisinskVurdering.HovedDiagnose()
                                    .apply {
                                        diagnosekode =
                                            CV().apply {
                                                s = ICPC2.OID
                                                v = "L87"
                                                dn = "Bursitt/tendinitt/synovitt IKA"
                                            }
                                    }
                        }
                }
            val result = fixMissingAndICPC2BDiagnoser(helseOpplysningerArbeidsuforhet)

            result.medisinskVurdering.hovedDiagnose.diagnosekode.s shouldBe ICPC2.OID
            result.medisinskVurdering.hovedDiagnose.diagnosekode.v shouldBe "L87"
            result.medisinskVurdering.hovedDiagnose.diagnosekode.dn shouldBe
                "Bursitt/tendinitt/synovitt IKA"
        }

        test("Should NOT convert ICD10 hoveddiagnose") {
            val helseOpplysningerArbeidsuforhet =
                HelseOpplysningerArbeidsuforhet().apply {
                    medisinskVurdering =
                        HelseOpplysningerArbeidsuforhet.MedisinskVurdering().apply {
                            hovedDiagnose =
                                HelseOpplysningerArbeidsuforhet.MedisinskVurdering.HovedDiagnose()
                                    .apply {
                                        diagnosekode =
                                            CV().apply {
                                                s = ICD10.OID
                                                v = "J06.9"
                                                dn = "Akutt øvre luftveisinfeksjon, uspesifisert"
                                            }
                                    }
                        }
                }

            val result = fixMissingAndICPC2BDiagnoser(helseOpplysningerArbeidsuforhet)

            result.medisinskVurdering.hovedDiagnose.diagnosekode.s shouldBe
                "2.16.578.1.12.4.1.1.7110"
            result.medisinskVurdering.hovedDiagnose.diagnosekode.v shouldBe "J06.9"
            result.medisinskVurdering.hovedDiagnose.diagnosekode.dn shouldBe
                "Akutt øvre luftveisinfeksjon, uspesifisert"
        }

        test("Should convert ICPC2B bidiagnose to ICPC2 but leave ICPC2 bidiagnose unchanged") {
            val bidiagnoseICPC2B =
                CV().apply {
                    s = ICPC2B.OID
                    v = "L87.0001"
                    dn = "Bursitt/tendinitt/synovitt IKA"
                }

            val bidiagnoseICPC2 =
                CV().apply {
                    s = ICPC2.OID
                    v = "A99"
                    dn = "Helseproblem/sykdom IKA"
                }

            val bidiagnoseICD10 =
                CV().apply {
                    s = ICD10.OID
                    v = "M79.3"
                    dn = "Uspesifisert bløtvevslidelse"
                }

            val helseOpplysningerArbeidsuforhet =
                HelseOpplysningerArbeidsuforhet().apply {
                    medisinskVurdering =
                        HelseOpplysningerArbeidsuforhet.MedisinskVurdering().apply {
                            hovedDiagnose =
                                HelseOpplysningerArbeidsuforhet.MedisinskVurdering.HovedDiagnose()
                                    .apply {
                                        diagnosekode =
                                            CV().apply {
                                                s = ICPC2.OID
                                                v = "L99"
                                                dn = "Sykdom muskel-skjelettsystem IKA"
                                            }
                                    }
                            biDiagnoser =
                                HelseOpplysningerArbeidsuforhet.MedisinskVurdering.BiDiagnoser()
                                    .apply {
                                        diagnosekode.addAll(
                                            listOf(
                                                bidiagnoseICPC2B,
                                                bidiagnoseICPC2,
                                                bidiagnoseICD10
                                            )
                                        )
                                    }
                        }
                }

            val result = fixMissingAndICPC2BDiagnoser(helseOpplysningerArbeidsuforhet)

            val biDiagnoser = result.medisinskVurdering.biDiagnoser.diagnosekode

            biDiagnoser[0].s shouldBe ICPC2.OID
            biDiagnoser[0].v shouldBe "L87"
            biDiagnoser[0].dn shouldBe "Bursitt/tendinitt/synovitt IKA"

            biDiagnoser[1].s shouldBe ICPC2.OID
            biDiagnoser[1].v shouldBe "A99"
            biDiagnoser[1].dn shouldBe "Helseproblem/sykdom IKA"

            biDiagnoser[2].s shouldBe "2.16.578.1.12.4.1.1.7110"
            biDiagnoser[2].v shouldBe "M79.3"
            biDiagnoser[2].dn shouldBe "Uspesifisert bløtvevslidelse"
        }

        test("Should add A99 diagnosis when hovedDiagnose is null and annenFraversArsak exists") {
            val helseOpplysningerArbeidsuforhet =
                HelseOpplysningerArbeidsuforhet().apply {
                    medisinskVurdering =
                        HelseOpplysningerArbeidsuforhet.MedisinskVurdering().apply {
                            hovedDiagnose = null
                            annenFraversArsak = ArsakType()
                        }
                }

            val result = fixMissingAndICPC2BDiagnoser(helseOpplysningerArbeidsuforhet)

            result.medisinskVurdering.hovedDiagnose.diagnosekode.v shouldBe "A99"
            result.medisinskVurdering.hovedDiagnose.diagnosekode.s shouldBe
                "2.16.578.1.12.4.1.1.7170"
            result.medisinskVurdering.hovedDiagnose.diagnosekode.dn shouldBe
                "Helseproblem/sykdom IKA"
        }
    })
