package no.nav.syfo

import no.kith.xmlstds.msghead._2006_05_24.XMLDocument
import no.kith.xmlstds.msghead._2006_05_24.XMLMsgHead
import no.kith.xmlstds.msghead._2006_05_24.XMLRefDoc
import no.nav.helse.infotrygd.foresp.InfotrygdForesp
import no.nav.helse.infotrygd.foresp.StatusType
import no.nav.helse.infotrygd.foresp.TypeMottakerKode
import no.nav.helse.infotrygd.foresp.TypeNavn
import no.nav.helse.infotrygd.foresp.TypeSMinfo
import no.nav.helse.sm2013.ArsakType
import no.nav.helse.sm2013.CV
import no.nav.helse.sm2013.HelseOpplysningerArbeidsuforhet
import no.nav.helse.sm2013.Ident
import no.trygdeetaten.xml.eiff._1.XMLEIFellesformat
import org.amshove.kluent.shouldEqual
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.LocalDate
import java.time.LocalDateTime

object CreateInfotrygdInfoSpek : Spek({

    describe("Testing mapping of fellesformat and InfotrygdInfo") {
        it("Should map correctly") {
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
                                mottakerKode = TypeMottakerKode.LE
                            }
                    )
                }
                sMhistorikk =  InfotrygdForesp.SMhistorikk().apply {
                     sykmelding.add(TypeSMinfo().apply {
                                status = StatusType().apply {
                                    returStatus = "0".toBigInteger()
                                    kodeMelding = "00"
                                    tidsStempel = LocalDateTime.now()
                                }
                                periode = TypeSMinfo.Periode().apply {
                                    arbufoerFOM = LocalDate.now()
                                }
                            }
                    )
                    tidsstempel = LocalDateTime.now()
                }
                hovedDiagnosekodeverk = "5"
            }
            val itfh = InfotrygdForespAndHealthInformation(infotrygdForesp, healthInformation)
            val infotrygdFellesformat = createInfotrygdInfo(fellesFormatString, itfh)

            extractHelseOpplysningerArbeidsuforhet(infotrygdFellesformat).regelSettVersjon shouldEqual
                    extractHelseOpplysningerArbeidsuforhet(fellesformat).regelSettVersjon
        }
    }
})

inline fun <reified T> XMLEIFellesformat.get() = this.any.find { it is T } as T

fun extractHelseOpplysningerArbeidsuforhet(fellesformat: XMLEIFellesformat): HelseOpplysningerArbeidsuforhet =
        fellesformat.get<XMLMsgHead>().document[0].refDoc.content.any[0] as HelseOpplysningerArbeidsuforhet