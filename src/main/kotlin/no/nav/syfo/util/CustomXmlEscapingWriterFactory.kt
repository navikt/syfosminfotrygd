package no.nav.syfo.util

import org.apache.commons.lang.StringEscapeUtils
import org.codehaus.stax2.io.EscapingWriterFactory
import java.io.IOException
import java.io.OutputStream
import java.io.Writer

class CustomXmlEscapingWriterFactory : EscapingWriterFactory {
    override fun createEscapingWriterFor(out: Writer, enc: String): Writer {
        return object : Writer() {
            @Throws(IOException::class)
            override fun write(cbuf: CharArray, off: Int, len: Int) {
                var value = ""
                for (i in off until len) {
                    value += cbuf[i]
                }
                val escapedStr = StringEscapeUtils.escapeXml(value)
                out.write(escapedStr)
            }

            @Throws(IOException::class)
            override fun flush() {
                out.flush()
            }

            @Throws(IOException::class)
            override fun close() {
                out.close()
            }
        }
    }

    override fun createEscapingWriterFor(out: OutputStream, enc: String): Writer {
        throw IllegalArgumentException("not supported")
    }

}
