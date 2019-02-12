package no.nav.syfo

import no.kith.xmlstds.msghead._2006_05_24.XMLMsgHead
import no.nav.helse.infotrygd.foresp.InfotrygdForesp
import no.nav.helse.sm2013.HelseOpplysningerArbeidsuforhet
import no.nav.helse.sm2013.KontrollSystemBlokk
import no.nav.helse.sm2013.KontrollsystemBlokkType
import no.trygdeetaten.xml.eiff._1.XMLEIFellesformat
import no.trygdeetaten.xml.eiff._1.XMLMottakenhetBlokk

import javax.xml.bind.JAXBContext
import javax.xml.bind.Marshaller
import javax.xml.bind.Unmarshaller
import javax.xml.datatype.DatatypeFactory

val newInstance: DatatypeFactory = DatatypeFactory.newInstance()

val infotrygdSporringJaxBContext: JAXBContext = JAXBContext.newInstance(InfotrygdForesp::class.java)
val infotrygdSporringMarshaller: Marshaller = infotrygdSporringJaxBContext.createMarshaller()

val infotrygdSporringUnmarshaller: Unmarshaller = infotrygdSporringJaxBContext.createUnmarshaller()

val fellesformatJaxBContext: JAXBContext = JAXBContext.newInstance(XMLEIFellesformat::class.java,
        XMLMsgHead::class.java, XMLMottakenhetBlokk::class.java, HelseOpplysningerArbeidsuforhet::class.java,
        KontrollsystemBlokkType::class.java, KontrollSystemBlokk::class.java, InfotrygdForesp::class.java)
val fellesformatUnmarshaller: Unmarshaller = fellesformatJaxBContext.createUnmarshaller()

val fellesformatMarshaller: Marshaller = fellesformatJaxBContext.createMarshaller()
