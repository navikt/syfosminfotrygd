package no.nav.syfo

import no.kith.xmlstds.msghead._2006_05_24.XMLMsgHead
import no.nav.helse.sm2013.EIFellesformat
import no.nav.model.infotrygdSporing.InfotrygdForesp
import no.nav.model.ksof.EIKSOperasjonsformat
import no.nav.model.sm2013.HelseOpplysningerArbeidsuforhet
import no.trygdeetaten.xml.eiff._1.XMLMottakenhetBlokk

import javax.xml.bind.JAXBContext
import javax.xml.bind.Marshaller
import javax.xml.bind.Unmarshaller
import javax.xml.datatype.DatatypeFactory

val newInstance: DatatypeFactory = DatatypeFactory.newInstance()

val infotrygdSporringJaxBContext: JAXBContext = JAXBContext.newInstance(InfotrygdForesp::class.java)
val infotrygdSporringMarshaller: Marshaller = infotrygdSporringJaxBContext.createMarshaller()

val oprasjonJaxBContext: JAXBContext = JAXBContext.newInstance(EIKSOperasjonsformat.Operasjon::class.java)
val oprasjonMarshaller: Marshaller = oprasjonJaxBContext.createMarshaller()

val infotrygdSporringUnmarshaller: Unmarshaller = infotrygdSporringJaxBContext.createUnmarshaller()

val fellesformatJaxBContext: JAXBContext = JAXBContext.newInstance(EIFellesformat::class.java, XMLMsgHead::class.java,
        XMLMottakenhetBlokk::class.java, HelseOpplysningerArbeidsuforhet::class.java)
val fellesformatUnmarshaller: Unmarshaller = fellesformatJaxBContext.createUnmarshaller()