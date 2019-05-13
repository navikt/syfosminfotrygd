package no.nav.syfo.util

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.dataformat.xml.JacksonXmlModule
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.dataformat.xml.ser.ToXmlGenerator
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.jaxb.JaxbAnnotationModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.migesok.jaxb.adapter.javatime.LocalDateTimeXmlAdapter
import com.migesok.jaxb.adapter.javatime.LocalDateXmlAdapter
import no.kith.xmlstds.msghead._2006_05_24.XMLMsgHead
import no.nav.helse.infotrygd.foresp.InfotrygdForesp
import no.nav.helse.sm2013.HelseOpplysningerArbeidsuforhet
import no.nav.helse.sm2013.KontrollSystemBlokk
import no.nav.helse.sm2013.KontrollsystemBlokkType
import no.nav.syfo.XMLDateAdapter
import no.nav.syfo.XMLDateTimeAdapter
import no.trygdeetaten.xml.eiff._1.XMLEIFellesformat
import no.trygdeetaten.xml.eiff._1.XMLMottakenhetBlokk

import javax.xml.bind.JAXBContext
import javax.xml.bind.Marshaller
import javax.xml.bind.Unmarshaller

val infotrygdSporringJaxBContext: JAXBContext = JAXBContext.newInstance(InfotrygdForesp::class.java)
val infotrygdSporringMarshaller: Marshaller = infotrygdSporringJaxBContext.createMarshaller()

val infotrygdSporringUnmarshaller: Unmarshaller = infotrygdSporringJaxBContext.createUnmarshaller()

val fellesformatJaxBContext: JAXBContext = JAXBContext.newInstance(XMLEIFellesformat::class.java,
        XMLMsgHead::class.java, XMLMottakenhetBlokk::class.java, HelseOpplysningerArbeidsuforhet::class.java,
        KontrollsystemBlokkType::class.java, KontrollSystemBlokk::class.java, InfotrygdForesp::class.java)
val fellesformatUnmarshaller: Unmarshaller = fellesformatJaxBContext.createUnmarshaller().apply {
    setAdapter(LocalDateTimeXmlAdapter::class.java, XMLDateTimeAdapter())
    setAdapter(LocalDateXmlAdapter::class.java, XMLDateAdapter())
}

val fellesformatMarshaller: Marshaller = fellesformatJaxBContext.createMarshaller().apply {
            setProperty(Marshaller.JAXB_ENCODING, "ISO-8859-1")
}

val xmlObjectWriter: ObjectMapper = XmlMapper(JacksonXmlModule().apply {
    setDefaultUseWrapper(false) })
        .configure(ToXmlGenerator.Feature.WRITE_XML_DECLARATION, true)
        .registerModule(JaxbAnnotationModule())
        .registerKotlinModule()
        .registerModule(JavaTimeModule())
        .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)