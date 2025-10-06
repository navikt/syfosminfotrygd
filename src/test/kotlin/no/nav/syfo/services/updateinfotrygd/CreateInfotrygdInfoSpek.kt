package no.nav.syfo.services.updateinfotrygd

import com.fasterxml.jackson.module.kotlin.readValue
import io.kotest.core.spec.style.FunSpec
import java.io.StringReader
import java.time.LocalDate
import java.time.LocalDateTime
import no.nav.helse.eiFellesformat.XMLEIFellesformat
import no.nav.helse.infotrygd.foresp.InfotrygdForesp
import no.nav.helse.infotrygd.foresp.TypeSMinfo
import no.nav.helse.msgHead.XMLDocument
import no.nav.helse.msgHead.XMLMsgHead
import no.nav.helse.msgHead.XMLRefDoc
import no.nav.helse.sm2013.ArsakType
import no.nav.helse.sm2013.CV
import no.nav.helse.sm2013.HelseOpplysningerArbeidsuforhet
import no.nav.helse.sm2013.KontrollSystemBlokk
import no.nav.syfo.InfotrygdForespAndHealthInformation
import no.nav.syfo.createDefaultHealthInformation
import no.nav.syfo.extractHelseOpplysningerArbeidsuforhet
import no.nav.syfo.get
import no.nav.syfo.getFileAsString
import no.nav.syfo.model.HelsepersonellKategori
import no.nav.syfo.objectMapper
import no.nav.syfo.services.createInfotrygdForesp
import no.nav.syfo.services.toInfotrygdForespValues
import no.nav.syfo.toString
import no.nav.syfo.util.LoggingMeta
import no.nav.syfo.util.fellesformatMarshaller
import no.nav.syfo.util.fellesformatUnmarshaller
import no.nav.syfo.util.xmlObjectWriter
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeFalse

class CreateInfotrygdInfoSpek :
    FunSpec(
        {
            context("Testing mapping of fellesformat and InfotrygdInfo") {
                test("Should map regelSettVersjon correctly") {
                    val healthInformation = createDefaultHealthInformation()
                    healthInformation.kontaktMedPasient =
                        HelseOpplysningerArbeidsuforhet.KontaktMedPasient().apply {
                            kontaktDato = LocalDate.now()
                            behandletDato = LocalDateTime.now()
                        }
                    val fellesFormat = createFellesFormat(healthInformation)
                    val infotrygdForesp =
                        createInfotrygdForesp(
                            "1231234",
                            healthInformation.toInfotrygdForespValues(),
                            "135153245",
                            ""
                        )
                    val fellesFormatString = fellesformatMarshaller.toString(fellesFormat)
                    val itfh =
                        InfotrygdForespAndHealthInformation(infotrygdForesp, healthInformation)

                    val infotrygdFellesformat =
                        createInfotrygdFellesformat(
                            fellesFormatString,
                            itfh,
                            healthInformation.aktivitet.periode.first(),
                            "1231234",
                            LocalDate.now(),
                            "LE",
                            "1341515",
                            LoggingMeta("mottakId", "12315", "", ""),
                            "0435",
                            LocalDate.now(),
                            behandletAvManuell = false,
                            utenlandskSykmelding = false,
                        )

                    extractHelseOpplysningerArbeidsuforhet(infotrygdFellesformat)
                        .regelSettVersjon shouldBeEqualTo
                        extractHelseOpplysningerArbeidsuforhet(fellesFormat).regelSettVersjon
                }

                test("Should map strekkode correctly") {
                    val healthInformation = createDefaultHealthInformation()
                    healthInformation.kontaktMedPasient =
                        HelseOpplysningerArbeidsuforhet.KontaktMedPasient().apply {
                            kontaktDato = LocalDate.now()
                            behandletDato = LocalDateTime.now()
                        }
                    val fellesFormat = createFellesFormat(healthInformation)
                    val infotrygdForesp =
                        createInfotrygdForesp(
                            "1231234",
                            healthInformation.toInfotrygdForespValues(),
                            "135153245",
                            ""
                        )
                    val fellesFormatString = fellesformatMarshaller.toString(fellesFormat)
                    val itfh =
                        InfotrygdForespAndHealthInformation(infotrygdForesp, healthInformation)

                    val infotrygdFellesformat =
                        createInfotrygdFellesformat(
                            fellesFormatString,
                            itfh,
                            healthInformation.aktivitet.periode.first(),
                            "1231234",
                            LocalDate.now(),
                            "LE",
                            "1341515",
                            LoggingMeta("mottakId", "12315", "", ""),
                            "0435",
                            LocalDate.now(),
                            behandletAvManuell = false,
                            utenlandskSykmelding = false,
                        )

                    extractHelseOpplysningerArbeidsuforhet(infotrygdFellesformat)
                        .aktivitet
                        .periode
                        .first()
                        .periodeTOMDato shouldBeEqualTo
                        extractHelseOpplysningerArbeidsuforhet(fellesFormat)
                            .aktivitet
                            .periode
                            .first()
                            .periodeTOMDato
                }

                test("Should use behandlingsDato instead of kontaktDato") {
                    val healthInformation = createDefaultHealthInformation()
                    healthInformation.kontaktMedPasient =
                        HelseOpplysningerArbeidsuforhet.KontaktMedPasient().apply {
                            kontaktDato = LocalDate.now().plusDays(1)
                            behandletDato = LocalDateTime.now()
                        }
                    val fellesFormat = createFellesFormat(healthInformation)
                    val infotrygdForesp =
                        createInfotrygdForesp(
                            "1231234",
                            healthInformation.toInfotrygdForespValues(),
                            "135153245",
                            ""
                        )
                    val fellesFormatString = fellesformatMarshaller.toString(fellesFormat)
                    val itfh =
                        InfotrygdForespAndHealthInformation(infotrygdForesp, healthInformation)

                    val infotrygdFellesformat =
                        createInfotrygdFellesformat(
                            fellesFormatString,
                            itfh,
                            healthInformation.aktivitet.periode.first(),
                            "1231234",
                            LocalDate.now(),
                            "LE",
                            "1341515",
                            LoggingMeta("mottakId", "12315", "", ""),
                            "0435",
                            LocalDate.now(),
                            behandletAvManuell = false,
                            utenlandskSykmelding = false,
                        )

                    val infotrygdBlokk =
                        infotrygdFellesformat.get<KontrollSystemBlokk>().infotrygdBlokk

                    healthInformation.kontaktMedPasient.behandletDato.toLocalDate() shouldBeEqualTo
                        infotrygdBlokk.first().behandlingsDato
                }

                test("Should use kontaktDato instead of behandlingsDato") {
                    val healthInformation = createDefaultHealthInformation()
                    healthInformation.kontaktMedPasient =
                        HelseOpplysningerArbeidsuforhet.KontaktMedPasient().apply {
                            kontaktDato = LocalDate.now()
                            behandletDato = LocalDateTime.now().plusDays(1)
                        }
                    val fellesFormat = createFellesFormat(healthInformation)
                    val infotrygdForesp =
                        createInfotrygdForesp(
                            "1231234",
                            healthInformation.toInfotrygdForespValues(),
                            "135153245",
                            ""
                        )
                    val fellesFormatString = fellesformatMarshaller.toString(fellesFormat)
                    val itfh =
                        InfotrygdForespAndHealthInformation(infotrygdForesp, healthInformation)

                    val infotrygdFellesformat =
                        createInfotrygdFellesformat(
                            fellesFormatString,
                            itfh,
                            healthInformation.aktivitet.periode.first(),
                            "1231234",
                            LocalDate.now(),
                            "LE",
                            "1341515",
                            LoggingMeta("mottakId", "12315", "", ""),
                            "0435",
                            LocalDate.now(),
                            behandletAvManuell = false,
                            utenlandskSykmelding = false,
                        )

                    val infotrygdBlokk =
                        infotrygdFellesformat.get<KontrollSystemBlokk>().infotrygdBlokk

                    healthInformation.kontaktMedPasient.kontaktDato shouldBeEqualTo
                        infotrygdBlokk.first().behandlingsDato
                }

                test("Should use arbeidsKategori to 01 when employers name is set") {
                    val healthInformation = createDefaultHealthInformation()
                    healthInformation.kontaktMedPasient =
                        HelseOpplysningerArbeidsuforhet.KontaktMedPasient().apply {
                            kontaktDato = LocalDate.now()
                            behandletDato = LocalDateTime.now()
                        }
                    healthInformation.arbeidsgiver =
                        HelseOpplysningerArbeidsuforhet.Arbeidsgiver().apply {
                            navnArbeidsgiver = "SAS as"
                        }
                    val fellesFormat = createFellesFormat(healthInformation)
                    val infotrygdForesp =
                        createInfotrygdForesp(
                            "1231234",
                            healthInformation.toInfotrygdForespValues(),
                            "135153245",
                            ""
                        )
                    val fellesFormatString = fellesformatMarshaller.toString(fellesFormat)
                    val itfh =
                        InfotrygdForespAndHealthInformation(infotrygdForesp, healthInformation)

                    val infotrygdFellesformat =
                        createInfotrygdFellesformat(
                            fellesFormatString,
                            itfh,
                            healthInformation.aktivitet.periode.first(),
                            "1231234",
                            LocalDate.now(),
                            HelsepersonellKategori.LEGE.verdi,
                            "1341515",
                            LoggingMeta("mottakId", "12315", "", ""),
                            "0435",
                            LocalDate.now(),
                            behandletAvManuell = false,
                            utenlandskSykmelding = false,
                        )

                    val infotrygdBlokk =
                        infotrygdFellesformat.get<KontrollSystemBlokk>().infotrygdBlokk

                    "01" shouldBeEqualTo infotrygdBlokk.first().arbeidsKategori
                }

                test("Should use arbeidsKategori to 30 when employers name is set") {
                    val healthInformation = createDefaultHealthInformation()
                    healthInformation.kontaktMedPasient =
                        HelseOpplysningerArbeidsuforhet.KontaktMedPasient().apply {
                            kontaktDato = LocalDate.now()
                            behandletDato = LocalDateTime.now()
                        }
                    val fellesFormat = createFellesFormat(healthInformation)
                    val infotrygdForesp =
                        createInfotrygdForesp(
                            "1231234",
                            healthInformation.toInfotrygdForespValues(),
                            "135153245",
                            ""
                        )
                    val fellesFormatString = fellesformatMarshaller.toString(fellesFormat)
                    val itfh =
                        InfotrygdForespAndHealthInformation(infotrygdForesp, healthInformation)

                    val infotrygdFellesformat =
                        createInfotrygdFellesformat(
                            fellesFormatString,
                            itfh,
                            healthInformation.aktivitet.periode.first(),
                            "1231234",
                            LocalDate.now(),
                            HelsepersonellKategori.LEGE.verdi,
                            "1341515",
                            LoggingMeta("mottakId", "12315", "", ""),
                            "0435",
                            LocalDate.now(),
                            behandletAvManuell = false,
                            utenlandskSykmelding = false,
                        )

                    val infotrygdBlokk =
                        infotrygdFellesformat.get<KontrollSystemBlokk>().infotrygdBlokk

                    "30" shouldBeEqualTo infotrygdBlokk.first().arbeidsKategori
                }

                test("Should not contain namespace in InfotrygdBlokk") {
                    val stringInput =
                        getFileAsString("src/test/resources/sykemelding2013Regelsettversjon2.xml")
                    val fellesformat =
                        fellesformatUnmarshaller.unmarshal(StringReader(stringInput))
                            as XMLEIFellesformat
                    val healthInformation = extractHelseOpplysningerArbeidsuforhet(fellesformat)
                    val infotrygdForesp =
                        createInfotrygdForesp(
                            "1231234",
                            healthInformation.toInfotrygdForespValues(),
                            "135153245",
                            ""
                        )
                    val fellesFormatString = fellesformatMarshaller.toString(fellesformat)
                    val itfh =
                        InfotrygdForespAndHealthInformation(infotrygdForesp, healthInformation)

                    val infotrygdFellesformat =
                        createInfotrygdFellesformat(
                            fellesFormatString,
                            itfh,
                            healthInformation.aktivitet.periode.first(),
                            "1231234",
                            LocalDate.now(),
                            HelsepersonellKategori.LEGE.verdi,
                            "1341515",
                            LoggingMeta("mottakId", "12315", "", ""),
                            "0435",
                            LocalDate.now(),
                            behandletAvManuell = false,
                            utenlandskSykmelding = false,
                        )

                    xmlObjectWriter
                        .writeValueAsString(infotrygdFellesformat)
                        .contains(":InfotrygdBlokk")
                        .shouldBeFalse()
                }

                test("Should NOT set arbeidsKategori") {
                    val healthInformation = createDefaultHealthInformation()
                    healthInformation.kontaktMedPasient =
                        HelseOpplysningerArbeidsuforhet.KontaktMedPasient().apply {
                            kontaktDato = LocalDate.now()
                            behandletDato = LocalDateTime.now()
                        }
                    healthInformation.aktivitet.periode.add(
                        HelseOpplysningerArbeidsuforhet.Aktivitet.Periode().apply {
                            periodeFOMDato = LocalDate.now().plusDays(5)
                            periodeTOMDato = LocalDate.now().plusDays(10)
                            aktivitetIkkeMulig =
                                HelseOpplysningerArbeidsuforhet.Aktivitet.Periode
                                    .AktivitetIkkeMulig()
                                    .apply { medisinskeArsaker = ArsakType().apply {} }
                        },
                    )
                    val fellesFormat = createFellesFormat(healthInformation)
                    val infotrygdForesp =
                        createInfotrygdForesp(
                            "1231234",
                            healthInformation.toInfotrygdForespValues(),
                            "135153245",
                            ""
                        )
                    val fellesFormatString = fellesformatMarshaller.toString(fellesFormat)
                    infotrygdForesp.sMhistorikk =
                        InfotrygdForesp.SMhistorikk().apply {
                            sykmelding.add(
                                TypeSMinfo().apply {
                                    periode =
                                        TypeSMinfo.Periode().apply { arbufoerFOM = LocalDate.now() }
                                },
                            )
                        }
                    val itfh =
                        InfotrygdForespAndHealthInformation(infotrygdForesp, healthInformation)

                    val infotrygdFellesformat =
                        createInfotrygdFellesformat(
                            fellesFormatString,
                            itfh,
                            healthInformation.aktivitet.periode[1],
                            "1231234",
                            LocalDate.now(),
                            HelsepersonellKategori.LEGE.verdi,
                            "1341515",
                            LoggingMeta("mottakId", "12315", "", ""),
                            "0435",
                            LocalDate.now(),
                            behandletAvManuell = false,
                            utenlandskSykmelding = false,
                            2,
                        )

                    val infotrygdBlokk =
                        infotrygdFellesformat.get<KontrollSystemBlokk>().infotrygdBlokk

                    infotrygdBlokk.last().arbeidsKategori shouldBeEqualTo null
                }


                test("Should throw exeption, on invalid diagnosekode s") {
                    val healthInformationString =
                        getFileAsString("src/test/resources/healthInformation.json")

                    val healthInformation: HelseOpplysningerArbeidsuforhet =
                        objectMapper.readValue(healthInformationString)
                    try {
                        createInfotrygdForesp(
                            "1231234",
                            healthInformation.toInfotrygdForespValues(),
                            "135153245",
                            ""
                        )
                    } catch (exception: Exception) {
                        exception.message shouldBeEqualTo
                            "Collection contains no element matching the predicate."
                    }
                }
            }
        },
    )

fun createFellesFormat(healthInformation: HelseOpplysningerArbeidsuforhet): XMLEIFellesformat =
    XMLEIFellesformat().apply {
        any.add(
            XMLMsgHead().apply {
                document.add(
                    XMLDocument().apply {
                        refDoc =
                            XMLRefDoc().apply {
                                content =
                                    XMLRefDoc.Content().apply {
                                        any.add(
                                            healthInformation,
                                        )
                                    }
                            }
                    },
                )
            },
        )
    }
