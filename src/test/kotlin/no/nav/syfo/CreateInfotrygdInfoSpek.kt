package no.nav.syfo

import no.kith.xmlstds.msghead._2006_05_24.XMLDocument
import no.kith.xmlstds.msghead._2006_05_24.XMLMsgHead
import no.kith.xmlstds.msghead._2006_05_24.XMLRefDoc
import no.nav.helse.sm2013.ArsakType
import no.nav.helse.sm2013.CV
import no.nav.helse.sm2013.HelseOpplysningerArbeidsuforhet
import no.nav.helse.sm2013.Ident
import no.nav.helse.sm2013.KontrollSystemBlokk
import no.nav.syfo.util.fellesformatMarshaller
import no.nav.syfo.util.xmlObjectWriter
import no.nav.syfo.util.fellesformatUnmarshaller
import no.trygdeetaten.xml.eiff._1.XMLEIFellesformat
import org.amshove.kluent.shouldBeFalse
import org.amshove.kluent.shouldEqual
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.io.StringReader
import java.time.LocalDate
import java.time.LocalDateTime

object CreateInfotrygdInfoSpek : Spek({

    describe("Testing mapping of fellesformat and InfotrygdInfo") {

        it("Should map regelSettVersjon correctly") {
            val healthInformation = createDefaultHealthInformation()

            healthInformation.kontaktMedPasient = HelseOpplysningerArbeidsuforhet.KontaktMedPasient().apply {
                kontaktDato = LocalDate.now()
                behandletDato = LocalDateTime.now()
            }

            val fellesFormat = createFellesFormat(healthInformation)
            val infotrygdForesp = createInfotrygdForesp("1231234", healthInformation, "135153245")

            val fellesFormatString = fellesformatMarshaller.toString(fellesFormat)

            val itfh = InfotrygdForespAndHealthInformation(infotrygdForesp, healthInformation)
            val infotrygdFellesformat = createInfotrygdInfo(fellesFormatString, itfh, "1231234", LocalDate.now(), "LE")

            extractHelseOpplysningerArbeidsuforhet(infotrygdFellesformat).regelSettVersjon shouldEqual
                    extractHelseOpplysningerArbeidsuforhet(fellesFormat).regelSettVersjon
        }

        it("Should map strekkode correctly") {
            val healthInformation = createDefaultHealthInformation()

            healthInformation.kontaktMedPasient = HelseOpplysningerArbeidsuforhet.KontaktMedPasient().apply {
                kontaktDato = LocalDate.now()
                behandletDato = LocalDateTime.now()
            }

            val fellesFormat = createFellesFormat(healthInformation)
            val infotrygdForesp = createInfotrygdForesp("1231234", healthInformation, "135153245")

            val fellesFormatString = fellesformatMarshaller.toString(fellesFormat)

            val itfh = InfotrygdForespAndHealthInformation(infotrygdForesp, healthInformation)
            val infotrygdFellesformat = createInfotrygdInfo(fellesFormatString, itfh, "1231234", LocalDate.now(), "LE")

            extractHelseOpplysningerArbeidsuforhet(infotrygdFellesformat).aktivitet.periode.first().periodeTOMDato shouldEqual
                    extractHelseOpplysningerArbeidsuforhet(fellesFormat).aktivitet.periode.first().periodeTOMDato
        }

        it("Should use behandlingsDato instead of kontaktDato") {
            val healthInformation = createDefaultHealthInformation()

            healthInformation.kontaktMedPasient = HelseOpplysningerArbeidsuforhet.KontaktMedPasient().apply {
                kontaktDato = LocalDate.now().plusDays(1)
                behandletDato = LocalDateTime.now()
            }

            val fellesFormat = createFellesFormat(healthInformation)
            val infotrygdForesp = createInfotrygdForesp("1231234", healthInformation, "135153245")

            val fellesFormatString = fellesformatMarshaller.toString(fellesFormat)

            val itfh = InfotrygdForespAndHealthInformation(infotrygdForesp, healthInformation)
            val infotrygdFellesformat = createInfotrygdInfo(fellesFormatString, itfh, "1231234", LocalDate.now(), "LE")

            val infotrygdBlokk = infotrygdFellesformat.get<KontrollSystemBlokk>().infotrygdBlokk

            healthInformation.kontaktMedPasient.behandletDato.toLocalDate() shouldEqual
                    infotrygdBlokk.first().behandlingsDato
        }

        it("Should use kontaktDato instead of behandlingsDato") {
            val healthInformation = createDefaultHealthInformation()

            healthInformation.kontaktMedPasient = HelseOpplysningerArbeidsuforhet.KontaktMedPasient().apply {
                kontaktDato = LocalDate.now()
                behandletDato = LocalDateTime.now().plusDays(1)
            }

            val fellesFormat = createFellesFormat(healthInformation)
            val infotrygdForesp = createInfotrygdForesp("1231234", healthInformation, "135153245")

            val fellesFormatString = fellesformatMarshaller.toString(fellesFormat)

            val itfh = InfotrygdForespAndHealthInformation(infotrygdForesp, healthInformation)
            val infotrygdFellesformat = createInfotrygdInfo(fellesFormatString, itfh, "1231234", LocalDate.now(), "LE")

            val infotrygdBlokk = infotrygdFellesformat.get<KontrollSystemBlokk>().infotrygdBlokk

            healthInformation.kontaktMedPasient.kontaktDato shouldEqual
                    infotrygdBlokk.first().behandlingsDato
        }

        it("Should use arbeidsKategori to 01 when employers name is set") {

            val healthInformation = createDefaultHealthInformation()

            healthInformation.kontaktMedPasient = HelseOpplysningerArbeidsuforhet.KontaktMedPasient().apply {
                kontaktDato = LocalDate.now()
                behandletDato = LocalDateTime.now()
            }

            healthInformation.arbeidsgiver = HelseOpplysningerArbeidsuforhet.Arbeidsgiver().apply {
                navnArbeidsgiver = "SAS as"
            }

            val fellesFormat = createFellesFormat(healthInformation)
            val infotrygdForesp = createInfotrygdForesp("1231234", healthInformation, "135153245")

            val fellesFormatString = fellesformatMarshaller.toString(fellesFormat)

            val itfh = InfotrygdForespAndHealthInformation(infotrygdForesp, healthInformation)
            val infotrygdFellesformat = createInfotrygdInfo(fellesFormatString, itfh, "1231234", LocalDate.now(), "LE")

            val infotrygdBlokk = infotrygdFellesformat.get<KontrollSystemBlokk>().infotrygdBlokk

            "01" shouldEqual infotrygdBlokk.first().arbeidsKategori
        }

        it("Should use arbeidsKategori to 030 when employers name is set") {
            val healthInformation = createDefaultHealthInformation()
            healthInformation.kontaktMedPasient = HelseOpplysningerArbeidsuforhet.KontaktMedPasient().apply {
                kontaktDato = LocalDate.now()
                behandletDato = LocalDateTime.now()
            }

            val fellesFormat = createFellesFormat(healthInformation)
            val infotrygdForesp = createInfotrygdForesp("1231234", healthInformation, "135153245")

            val fellesFormatString = fellesformatMarshaller.toString(fellesFormat)

            val itfh = InfotrygdForespAndHealthInformation(infotrygdForesp, healthInformation)
            val infotrygdFellesformat = createInfotrygdInfo(fellesFormatString, itfh, "1231234", LocalDate.now(), "LE")

            val infotrygdBlokk = infotrygdFellesformat.get<KontrollSystemBlokk>().infotrygdBlokk

            "030" shouldEqual infotrygdBlokk.first().arbeidsKategori
        }

        it("Should not contain namespace in InfotrygdBlokk") {
            val stringInput = getFileAsString("src/test/resources/sykemelding2013Regelsettversjon2.xml")
            val fellesformat = fellesformatUnmarshaller.unmarshal(StringReader(stringInput)) as XMLEIFellesformat

            val healthInformation = extractHelseOpplysningerArbeidsuforhet(fellesformat)
            val infotrygdForesp = createInfotrygdForesp("1231234", healthInformation, "135153245")

            val fellesFormatString = fellesformatMarshaller.toString(fellesformat)

            val itfh = InfotrygdForespAndHealthInformation(infotrygdForesp, healthInformation)
            val infotrygdFellesformat = createInfotrygdInfo(fellesFormatString, itfh, "1231234", LocalDate.now(), "LE")

            xmlObjectWriter.writeValueAsString(infotrygdFellesformat).contains(":InfotrygdBlokk").shouldBeFalse()
            println(xmlObjectWriter.writeValueAsString(infotrygdFellesformat))
        }

        it("Should NOT set arbeidsKategori") {
            val healthInformation = createDefaultHealthInformation()
            healthInformation.kontaktMedPasient = HelseOpplysningerArbeidsuforhet.KontaktMedPasient().apply {
                kontaktDato = LocalDate.now()
                behandletDato = LocalDateTime.now()
            }
            healthInformation.aktivitet.periode.add(
                    HelseOpplysningerArbeidsuforhet.Aktivitet.Periode().apply {
                        periodeFOMDato = LocalDate.now().plusDays(5)
                        periodeTOMDato = LocalDate.now().plusDays(10)
                        aktivitetIkkeMulig = HelseOpplysningerArbeidsuforhet.Aktivitet.Periode.AktivitetIkkeMulig().apply {
                            medisinskeArsaker = ArsakType().apply {
                            }
                        }
                    }

            )

            val fellesFormat = createFellesFormat(healthInformation)
            val infotrygdForesp = createInfotrygdForesp("1231234", healthInformation, "135153245")

            val fellesFormatString = fellesformatMarshaller.toString(fellesFormat)

            val itfh = InfotrygdForespAndHealthInformation(infotrygdForesp, healthInformation)
            val infotrygdFellesformat = createInfotrygdInfo(fellesFormatString, itfh, "1231234", LocalDate.now(), "LE")

            val infotrygdBlokk = infotrygdFellesformat.get<KontrollSystemBlokk>().infotrygdBlokk

            infotrygdBlokk.last().arbeidsKategori shouldEqual null
        }

        it("Should set set fields for first infotrygdblokk") {
            val healthInformation = createDefaultHealthInformation()
            healthInformation.kontaktMedPasient = HelseOpplysningerArbeidsuforhet.KontaktMedPasient().apply {
                kontaktDato = LocalDate.now()
                behandletDato = LocalDateTime.now()
            }
            healthInformation.aktivitet.periode.add(
                    HelseOpplysningerArbeidsuforhet.Aktivitet.Periode().apply {
                        periodeFOMDato = LocalDate.now().plusDays(5)
                        periodeTOMDato = LocalDate.now().plusDays(10)
                        aktivitetIkkeMulig = HelseOpplysningerArbeidsuforhet.Aktivitet.Periode.AktivitetIkkeMulig().apply {
                            medisinskeArsaker = ArsakType().apply {
                            }
                        }
                    }
            )
            healthInformation.medisinskVurdering = HelseOpplysningerArbeidsuforhet.MedisinskVurdering().apply {
                hovedDiagnose = HelseOpplysningerArbeidsuforhet.MedisinskVurdering.HovedDiagnose().apply {
                    diagnosekode = CV().apply {
                        dn = "Problem med jus/politi"
                        s = "2.16.578.1.12.4.1.1.7110"
                        v = "Z09"
                    }
                }
            }

            val fellesFormat = createFellesFormat(healthInformation)
            val infotrygdForesp = createInfotrygdForesp("1231234", healthInformation, "135153245")

            val fellesFormatString = fellesformatMarshaller.toString(fellesFormat)

            val itfh = InfotrygdForespAndHealthInformation(infotrygdForesp, healthInformation)
            val infotrygdFellesformat = createInfotrygdInfo(fellesFormatString, itfh, "1231234", LocalDate.now(), "LE")

            val infotrygdBlokk = infotrygdFellesformat.get<KontrollSystemBlokk>().infotrygdBlokk

            infotrygdBlokk.first().fodselsnummer shouldEqual "1231234"
            infotrygdBlokk.first().tkNummer shouldEqual ""
            infotrygdBlokk.first().forsteFravaersDag shouldEqual LocalDate.now()
            infotrygdBlokk.first().behandlingsDato shouldEqual LocalDate.now()
            infotrygdBlokk.first().hovedDiagnose shouldEqual "Z09"
            infotrygdBlokk.first().hovedDiagnoseGruppe shouldEqual "3".toBigInteger()
            infotrygdBlokk.first().hovedDiagnoseTekst shouldEqual "Problem med jus/politi"
            infotrygdBlokk.first().biDiagnose shouldEqual null
            infotrygdBlokk.first().biDiagnoseGruppe shouldEqual null
            infotrygdBlokk.first().biDiagnoseTekst shouldEqual null
            infotrygdBlokk.first().arbeidsKategori shouldEqual "030"
            infotrygdBlokk.first().gruppe shouldEqual "96"
            infotrygdBlokk.first().saksbehandler shouldEqual "Auto"
            infotrygdBlokk.first().arbeidsufoerTOM shouldEqual LocalDate.now().plusDays(4)
            infotrygdBlokk.first().ufoeregrad shouldEqual "100".toBigInteger()
        }

        it("Should set set fields for subsequent infotrygdblokk") {
            val healthInformation = createDefaultHealthInformation()
            healthInformation.kontaktMedPasient = HelseOpplysningerArbeidsuforhet.KontaktMedPasient().apply {
                kontaktDato = LocalDate.now()
                behandletDato = LocalDateTime.now()
            }
            healthInformation.aktivitet.periode.add(
                    HelseOpplysningerArbeidsuforhet.Aktivitet.Periode().apply {
                        periodeFOMDato = LocalDate.now().plusDays(5)
                        periodeTOMDato = LocalDate.now().plusDays(10)
                        aktivitetIkkeMulig = HelseOpplysningerArbeidsuforhet.Aktivitet.Periode.AktivitetIkkeMulig().apply {
                            medisinskeArsaker = ArsakType().apply {
                            }
                        }
                    }

            )
            healthInformation.medisinskVurdering = HelseOpplysningerArbeidsuforhet.MedisinskVurdering().apply {
                hovedDiagnose = HelseOpplysningerArbeidsuforhet.MedisinskVurdering.HovedDiagnose().apply {
                    diagnosekode = CV().apply {
                        dn = "Problem med jus/politi"
                        s = "2.16.578.1.12.4.1.1.7110"
                        v = "Z09"
                    }
                }
            }

            val fellesFormat = createFellesFormat(healthInformation)
            val infotrygdForesp = createInfotrygdForesp("1231234", healthInformation, "135153245")

            val fellesFormatString = fellesformatMarshaller.toString(fellesFormat)

            val itfh = InfotrygdForespAndHealthInformation(infotrygdForesp, healthInformation)
            val infotrygdFellesformat = createInfotrygdInfo(fellesFormatString, itfh, "1231234", LocalDate.now(), "LE")

            val infotrygdBlokk = infotrygdFellesformat.get<KontrollSystemBlokk>().infotrygdBlokk

            infotrygdBlokk[1].fodselsnummer shouldEqual "1231234"
            infotrygdBlokk[1].tkNummer shouldEqual ""
            infotrygdBlokk[1].forsteFravaersDag shouldEqual LocalDate.now()
            infotrygdBlokk[1].behandlingsDato shouldEqual null
            infotrygdBlokk[1].hovedDiagnose shouldEqual null
            infotrygdBlokk[1].hovedDiagnoseGruppe shouldEqual null
            infotrygdBlokk[1].hovedDiagnoseTekst shouldEqual null
            infotrygdBlokk[1].biDiagnose shouldEqual null
            infotrygdBlokk[1].biDiagnoseGruppe shouldEqual null
            infotrygdBlokk[1].biDiagnoseTekst shouldEqual null
            infotrygdBlokk[1].arbeidsKategori shouldEqual null
            infotrygdBlokk[1].gruppe shouldEqual null
            infotrygdBlokk[1].saksbehandler shouldEqual null
            infotrygdBlokk[1].arbeidsufoerTOM shouldEqual LocalDate.now().plusDays(10)
            infotrygdBlokk[1].ufoeregrad shouldEqual "100".toBigInteger()
        }
    }
})

fun createDefaultHealthInformation(): HelseOpplysningerArbeidsuforhet =
    HelseOpplysningerArbeidsuforhet().apply {
        regelSettVersjon = "1"
        aktivitet = HelseOpplysningerArbeidsuforhet.Aktivitet().apply {
            periode.add(
                    HelseOpplysningerArbeidsuforhet.Aktivitet.Periode().apply {
                        periodeFOMDato = LocalDate.now()
                        periodeTOMDato = LocalDate.now().plusDays(4)
                        aktivitetIkkeMulig = HelseOpplysningerArbeidsuforhet.Aktivitet.Periode.AktivitetIkkeMulig().apply {
                            medisinskeArsaker = ArsakType().apply {
                            }
                        }
                    }
            )
        }
        pasient = HelseOpplysningerArbeidsuforhet.Pasient().apply {
            fodselsnummer = Ident().apply {
                id = "12343567"
                typeId = CV().apply {
                    dn = "Fødselsnummer"
                    s = "2.16.578.1.12.4.1.1.8116"
                    v = "FNR"
                }
            }
        }
        syketilfelleStartDato = LocalDate.now()
        medisinskVurdering = HelseOpplysningerArbeidsuforhet.MedisinskVurdering().apply {
            hovedDiagnose = HelseOpplysningerArbeidsuforhet.MedisinskVurdering.HovedDiagnose().apply {
                diagnosekode = CV().apply {
                    dn = "Problem med jus/politi"
                    s = "2.16.578.1.12.4.1.1.7110"
                    v = "Z09"
                }
            }
        }
    }

fun createFellesFormat(healthInformation: HelseOpplysningerArbeidsuforhet): XMLEIFellesformat = XMLEIFellesformat().apply {
        any.add(
                XMLMsgHead().apply {
                    document.add(
                            XMLDocument().apply {
                                refDoc = XMLRefDoc().apply {
                                    content = XMLRefDoc.Content().apply {
                                        any.add(
                                                healthInformation
                                        )
                                    }
                                }
                            }
                    )
                }
        )
    }
