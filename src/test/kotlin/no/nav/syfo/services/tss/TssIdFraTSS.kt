package no.nav.syfo.services.tss

import io.kotest.core.spec.style.FunSpec
import no.nav.helse.tss.samhandler.data.XMLSamhAvdPraType
import no.nav.helse.tss.samhandler.data.XMLSamhandlerIDataB910Type
import no.nav.helse.tss.samhandler.data.XMLSamhandlerType
import no.nav.helse.tss.samhandler.data.XMLSvarStatusType
import no.nav.helse.tss.samhandler.data.XMLTOutputElementer
import no.nav.helse.tss.samhandler.data.XMLTServicerutiner
import no.nav.helse.tss.samhandler.data.XMLTidOFF1
import no.nav.helse.tss.samhandler.data.XMLTssSamhandlerData
import no.nav.helse.tss.samhandler.data.XMLTypeKomplett
import no.nav.helse.tss.samhandler.data.XMLTypeOD960
import no.nav.helse.tss.samhandler.data.XMLTypeSamhAvd
import no.nav.helse.tss.samhandler.data.XMLTypeSamhandler
import org.amshove.kluent.shouldBeEqualTo

class TssIdFraTSS : FunSpec({
    context("Testing av håndtering av data objeketer fra tss") {
        test("Should find tssid for avdnr 01") {
            val tssSamhandlerInfoResponse = XMLTssSamhandlerData()
            tssSamhandlerInfoResponse.tssInputData = XMLTssSamhandlerData.TssInputData().apply {
                tssServiceRutine = XMLTServicerutiner().apply {
                    samhandlerIDataB960 = XMLSamhandlerIDataB910Type().apply {
                        ofFid = XMLTidOFF1().apply {
                            idOff = "123134151"
                            kodeIdType = "FNR"
                        }
                        historikk = "J"
                    }
                }
            }
            tssSamhandlerInfoResponse.tssOutputData = XMLTOutputElementer().apply {
                svarStatus = XMLSvarStatusType().apply {
                    alvorligGrad = "00"
                }
                samhandlerODataB960 = XMLTypeOD960().apply {
                    antallSamhandlere = "1"
                    enkeltSamhandler.add(
                        XMLTypeKomplett().apply {
                            samhandler110 = XMLTypeSamhandler().apply {
                                antSamh = "1"
                                samhandler.add(
                                    XMLSamhandlerType().apply {
                                        idOff = "123134151"
                                        kodeIdentType = "FNR"
                                        kodeSamhType = "LE"
                                        beskSamhType = "Lege"
                                        datoSamhFom = "20041101"
                                        datoSamhTom = null
                                        navnSamh = "PER HANSEN"
                                        kodeSpraak = "NB"
                                        etatsMerke = null
                                        utbetSperre = "N"
                                        kodeKontrInt = "0006"
                                        beskrKontrInt = "Hver gang"
                                        kodeStatus = "GYLD"
                                        beskrStatus = "Gyldig"
                                        kilde = "HPR"
                                        brukerId = "INITLOAD"
                                        tidReg = "200510201032"
                                    },
                                )
                            }
                            samhandlerAvd125 = XMLTypeSamhAvd().apply {
                                antSamhAvd = "1"
                                samhAvd.add(
                                    (
                                        XMLSamhAvdPraType().apply {
                                            avdNr = "01"
                                            avdNavn = null
                                            typeAvd = null
                                            beskrTypeAvd = null
                                            datoAvdFom = "20041101"
                                            datoAvdTom = null
                                            gyldigAvd = "J"
                                            idOffTSS = "80000007415"
                                            offNrAvd = null
                                            kilde = "HPR"
                                            brukerId = "INITLOAD"
                                            tidReg = "200510201032"
                                        }
                                        ),
                                )
                            }
                        },
                    )
                }
            }

            finnTssIdFraTSSRespons(tssSamhandlerInfoResponse) shouldBeEqualTo "80000007415"
        }
        test("Should set kodeIdType to FNR") {
            val kodeIdType = setFnrOrDnr("04030350265")
            kodeIdType shouldBeEqualTo "FNR"
        }

        test("Should set kodeIdType to DNR") {
            val kodeIdType = setFnrOrDnr("41019111197")
            kodeIdType shouldBeEqualTo "DNR"
        }
    }
})
