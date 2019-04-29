package no.nav.syfo

import no.kith.xmlstds.msghead._2006_05_24.XMLDocument
import no.kith.xmlstds.msghead._2006_05_24.XMLMsgHead
import no.kith.xmlstds.msghead._2006_05_24.XMLRefDoc
import no.nav.helse.infotrygd.foresp.InfotrygdForesp
import no.nav.helse.infotrygd.foresp.StatusType
import no.nav.helse.infotrygd.foresp.TypeNavn
import no.nav.helse.infotrygd.foresp.TypeSMinfo
import no.nav.helse.sm2013.ArsakType
import no.nav.helse.sm2013.CV
import no.nav.helse.sm2013.HelseOpplysningerArbeidsuforhet
import no.nav.helse.sm2013.Ident
import no.nav.helse.sm2013.KontrollSystemBlokk
import no.nav.syfo.util.fellesformatMarshaller
import no.trygdeetaten.xml.eiff._1.XMLEIFellesformat
import org.amshove.kluent.shouldEqual
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.LocalDate
import java.time.LocalDateTime

object CreateInfotrygdInfoSpek : Spek({

    describe("Testing mapping of fellesformat and InfotrygdInfo") {

        val healthInformation = HelseOpplysningerArbeidsuforhet().apply {
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

        val fellesformat = XMLEIFellesformat().apply {
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

        val fellesFormatString = fellesformatMarshaller.toString(fellesformat)
        val infotrygdForesp = InfotrygdForesp().apply {
            hovedStatus = StatusType().apply {
                kodeMelding = "00"
            }
            behandlerInfo = InfotrygdForesp.BehandlerInfo().apply {
                behandler.add(
                        InfotrygdForesp.BehandlerInfo.Behandler().apply {
                            navn = TypeNavn().apply {
                                fornavn = "Per"
                                etternavn = "Hansne"
                            }
                        }
                )
            }
            sMhistorikk = InfotrygdForesp.SMhistorikk().apply {
                sykmelding.add(TypeSMinfo().apply {
                    status = StatusType().apply {
                        returStatus = "0".toBigInteger()
                        kodeMelding = "04"
                        tidsStempel = LocalDateTime.now()
                    }
                    periode = TypeSMinfo.Periode().apply {
                        arbufoerFOM = LocalDate.now()
                    }
                })
                tidsstempel = LocalDateTime.now()
            }
            hovedDiagnosekodeverk = "5"
        }

        it("Should map regelSettVersjon correctly") {
            healthInformation.kontaktMedPasient = HelseOpplysningerArbeidsuforhet.KontaktMedPasient().apply {
                kontaktDato = LocalDate.now()
                behandletDato = LocalDateTime.now()
            }

            val itfh = InfotrygdForespAndHealthInformation(infotrygdForesp, healthInformation)
            val infotrygdFellesformat = createInfotrygdInfo(fellesFormatString, itfh, "1231234")

            extractHelseOpplysningerArbeidsuforhet(infotrygdFellesformat).regelSettVersjon shouldEqual
                    extractHelseOpplysningerArbeidsuforhet(fellesformat).regelSettVersjon
        }

        it("Should map strekkode correctly") {
            healthInformation.kontaktMedPasient = HelseOpplysningerArbeidsuforhet.KontaktMedPasient().apply {
                kontaktDato = LocalDate.now()
                behandletDato = LocalDateTime.now()
            }

            val itfh = InfotrygdForespAndHealthInformation(infotrygdForesp, healthInformation)
            val infotrygdFellesformat = createInfotrygdInfo(fellesFormatString, itfh, "1231234")

            extractHelseOpplysningerArbeidsuforhet(infotrygdFellesformat).aktivitet.periode.first().periodeTOMDato shouldEqual
                    extractHelseOpplysningerArbeidsuforhet(fellesformat).aktivitet.periode.first().periodeTOMDato
        }

        it("Should use behandlingsDato instead of kontaktDato") {
            healthInformation.kontaktMedPasient = HelseOpplysningerArbeidsuforhet.KontaktMedPasient().apply {
                kontaktDato = LocalDate.now().plusDays(1)
                behandletDato = LocalDateTime.now()
            }

            val itfh = InfotrygdForespAndHealthInformation(infotrygdForesp, healthInformation)
            val infotrygdFellesformat = createInfotrygdInfo(fellesFormatString, itfh, "1231234")

            val infotrygdBlokk = infotrygdFellesformat.get<KontrollSystemBlokk>().infotrygdBlokk

            healthInformation.kontaktMedPasient.behandletDato.toLocalDate() shouldEqual
                    infotrygdBlokk.first().behandlingsDato
        }

        it("Should use kontaktDato instead of behandlingsDato") {
            healthInformation.kontaktMedPasient = HelseOpplysningerArbeidsuforhet.KontaktMedPasient().apply {
                kontaktDato = LocalDate.now()
                behandletDato = LocalDateTime.now().plusDays(1)
            }

            val itfh = InfotrygdForespAndHealthInformation(infotrygdForesp, healthInformation)
            val infotrygdFellesformat = createInfotrygdInfo(fellesFormatString, itfh, "1231234")

            val infotrygdBlokk = infotrygdFellesformat.get<KontrollSystemBlokk>().infotrygdBlokk

            healthInformation.kontaktMedPasient.kontaktDato shouldEqual
                    infotrygdBlokk.first().behandlingsDato
        }

        it("Should use arbeidsKategori to 01 when employers name is set") {
            healthInformation.kontaktMedPasient = HelseOpplysningerArbeidsuforhet.KontaktMedPasient().apply {
                kontaktDato = LocalDate.now()
                behandletDato = LocalDateTime.now()
            }

            healthInformation.arbeidsgiver = HelseOpplysningerArbeidsuforhet.Arbeidsgiver().apply {
                navnArbeidsgiver = "SAS as"
            }

            val itfh = InfotrygdForespAndHealthInformation(infotrygdForesp, healthInformation)
            val infotrygdFellesformat = createInfotrygdInfo(fellesFormatString, itfh, "1231234")

            val infotrygdBlokk = infotrygdFellesformat.get<KontrollSystemBlokk>().infotrygdBlokk

            "01" shouldEqual infotrygdBlokk.first().arbeidsKategori
        }

        it("Should use arbeidsKategori to 030 when employers name is set") {
            healthInformation.kontaktMedPasient = HelseOpplysningerArbeidsuforhet.KontaktMedPasient().apply {
                kontaktDato = LocalDate.now()
                behandletDato = LocalDateTime.now()
            }

            val itfh = InfotrygdForespAndHealthInformation(infotrygdForesp, healthInformation)
            val infotrygdFellesformat = createInfotrygdInfo(fellesFormatString, itfh, "1231234")

            val infotrygdBlokk = infotrygdFellesformat.get<KontrollSystemBlokk>().infotrygdBlokk

            "030" shouldEqual infotrygdBlokk.first().arbeidsKategori
        }
    }
})