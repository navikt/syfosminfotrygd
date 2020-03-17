package no.nav.syfo

import no.nav.helse.tssSamhandlerData.XMLSamhAvdPraType
import no.nav.helse.tssSamhandlerData.XMLSamhandlerIDataB910Type
import no.nav.helse.tssSamhandlerData.XMLSamhandlerType
import no.nav.helse.tssSamhandlerData.XMLSvarStatusType
import no.nav.helse.tssSamhandlerData.XMLTOutputElementer
import no.nav.helse.tssSamhandlerData.XMLTServicerutiner
import no.nav.helse.tssSamhandlerData.XMLTidOFF1
import no.nav.helse.tssSamhandlerData.XMLTssSamhandlerData
import no.nav.helse.tssSamhandlerData.XMLTypeKomplett
import no.nav.helse.tssSamhandlerData.XMLTypeOD960
import no.nav.helse.tssSamhandlerData.XMLTypeSamhAvd
import no.nav.helse.tssSamhandlerData.XMLTypeSamhandler
import org.amshove.kluent.shouldEqual
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object TssIdFraTSS : Spek({
    describe("Testing av håndtering av data objeketer fra tss") {
        it("Should find tssid for avdnr 01") {
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
                    enkeltSamhandler.add(XMLTypeKomplett().apply {
                        samhandler110 = XMLTypeSamhandler().apply {
                            antSamh = "1"
                            samhandler.add(XMLSamhandlerType().apply {
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
                            })
                        }
                        samhandlerAvd125 = XMLTypeSamhAvd().apply {
                            antSamhAvd = "1"
                            samhAvd.add((XMLSamhAvdPraType().apply {
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
                            }))
                        }
                    })
                }
            }

            finnTssIdFraTSSRespons(tssSamhandlerInfoResponse) shouldEqual "80000007415"
        }
    }
})
