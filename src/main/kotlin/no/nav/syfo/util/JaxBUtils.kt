package no.nav.syfo.util

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.dataformat.xml.JacksonXmlModule
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.dataformat.xml.ser.ToXmlGenerator
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.jaxb.JaxbAnnotationModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.migesok.jaxb.adapter.javatime.LocalDateTimeXmlAdapter
import com.migesok.jaxb.adapter.javatime.LocalDateXmlAdapter
import no.nav.helse.eiFellesformat.XMLEIFellesformat
import no.nav.helse.eiFellesformat.XMLMottakenhetBlokk
import no.nav.helse.infotrygd.foresp.InfotrygdForesp
import no.nav.helse.msgHead.XMLMsgHead
import no.nav.helse.sm2013.HelseOpplysningerArbeidsuforhet
import no.nav.helse.sm2013.KontrollSystemBlokk
import no.nav.helse.sm2013.KontrollsystemBlokkType
import org.codehaus.stax2.XMLOutputFactory2
import javax.xml.bind.JAXBContext
import javax.xml.bind.Marshaller
import javax.xml.bind.Unmarshaller

val infotrygdSporringJaxBContext: JAXBContext = JAXBContext.newInstance(InfotrygdForesp::class.java)
val infotrygdSporringMarshaller: Marshaller = infotrygdSporringJaxBContext.createMarshaller()
val infotrygdSporringUnmarshaller: Unmarshaller = infotrygdSporringJaxBContext.createUnmarshaller()

val fellesformatJaxBContext: JAXBContext = JAXBContext.newInstance(
    XMLEIFellesformat::class.java,
    XMLMsgHead::class.java,
    XMLMottakenhetBlokk::class.java,
    HelseOpplysningerArbeidsuforhet::class.java,
    KontrollsystemBlokkType::class.java,
    KontrollSystemBlokk::class.java,
    InfotrygdForesp::class.java,
)
val fellesformatUnmarshaller: Unmarshaller = fellesformatJaxBContext.createUnmarshaller().apply {
    setAdapter(LocalDateTimeXmlAdapter::class.java, XMLDateTimeAdapter())
    setAdapter(LocalDateXmlAdapter::class.java, XMLDateAdapter())
}

val fellesformatMarshaller: Marshaller = fellesformatJaxBContext.createMarshaller().apply {
    setProperty(Marshaller.JAXB_ENCODING, "ISO-8859-1")
}

val xmlObjectWriter: XmlMapper = (
    XmlMapper(
        JacksonXmlModule().apply {
            setDefaultUseWrapper(false)
        },
    )
        .configure(ToXmlGenerator.Feature.WRITE_XML_DECLARATION, true)
        .registerModule(JaxbAnnotationModule())
        .registerKotlinModule()
        .registerModule(JavaTimeModule())
        .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false) as XmlMapper
    )
    .apply {
        factory.xmlOutputFactory.setProperty(XMLOutputFactory2.P_TEXT_ESCAPER, CustomXmlEscapingWriterFactory)
        factory.xmlOutputFactory.setProperty(XMLOutputFactory2.P_ATTR_VALUE_ESCAPER, CustomXmlEscapingWriterFactory)
    }
