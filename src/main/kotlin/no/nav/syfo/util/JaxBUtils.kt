package no.nav.syfo.util

import com.migesok.jaxb.adapter.javatime.LocalDateTimeXmlAdapter
import com.migesok.jaxb.adapter.javatime.LocalDateXmlAdapter
import javax.xml.bind.JAXBContext
import javax.xml.bind.Marshaller
import javax.xml.bind.Unmarshaller
import no.nav.helse.eiFellesformat.XMLEIFellesformat
import no.nav.helse.eiFellesformat.XMLMottakenhetBlokk
import no.nav.helse.infotrygd.foresp.InfotrygdForesp
import no.nav.helse.msgHead.XMLMsgHead
import no.nav.helse.sm2013.HelseOpplysningerArbeidsuforhet
import no.nav.helse.sm2013.KontrollSystemBlokk
import no.nav.helse.sm2013.KontrollsystemBlokkType
import org.codehaus.stax2.XMLOutputFactory2
import tools.jackson.databind.MapperFeature
import tools.jackson.databind.cfg.DateTimeFeature
import tools.jackson.dataformat.xml.XmlMapper
import tools.jackson.dataformat.xml.XmlWriteFeature
import tools.jackson.module.jaxb.JaxbAnnotationModule

val infotrygdSporringJaxBContext: JAXBContext = JAXBContext.newInstance(InfotrygdForesp::class.java)

val fellesformatJaxBContext: JAXBContext =
    JAXBContext.newInstance(
        XMLEIFellesformat::class.java,
        XMLMsgHead::class.java,
        XMLMottakenhetBlokk::class.java,
        HelseOpplysningerArbeidsuforhet::class.java,
        KontrollsystemBlokkType::class.java,
        KontrollSystemBlokk::class.java,
        InfotrygdForesp::class.java,
    )
val fellesformatUnmarshaller: Unmarshaller
    get() =
        fellesformatJaxBContext.createUnmarshaller().apply {
            setAdapter(LocalDateTimeXmlAdapter::class.java, XMLDateTimeAdapter())
            setAdapter(LocalDateXmlAdapter::class.java, XMLDateAdapter())
        }

val fellesformatMarshaller: Marshaller
    get() =
        fellesformatJaxBContext.createMarshaller().apply {
            setProperty(Marshaller.JAXB_ENCODING, "ISO-8859-1")
        }

val xmlObjectWriter: XmlMapper =
    XmlMapper.builder()
        .defaultUseWrapper(false)
        .enable(XmlWriteFeature.WRITE_XML_DECLARATION)
        .addModule(JaxbAnnotationModule())
        .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
        .disable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
        .build()
        .apply {
            tokenStreamFactory()
                .xmlOutputFactory
                .setProperty(XMLOutputFactory2.P_TEXT_ESCAPER, CustomXmlEscapingWriterFactory)
            tokenStreamFactory()
                .xmlOutputFactory
                .setProperty(XMLOutputFactory2.P_ATTR_VALUE_ESCAPER, CustomXmlEscapingWriterFactory)
        }
