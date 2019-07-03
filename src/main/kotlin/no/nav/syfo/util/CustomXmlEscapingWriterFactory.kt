package no.nav.syfo.util

import org.codehaus.stax2.io.EscapingWriterFactory
import java.io.IOException
import java.io.OutputStream
import java.io.Writer

object CustomXmlEscapingWriterFactory : EscapingWriterFactory {
    override fun createEscapingWriterFor(writer: Writer, enc: String): Writer {
        return object : Writer() {
            @Throws(IOException::class)
            override fun write(cbuf: CharArray, off: Int, len: Int) {
                var value = ""
                for (i in off until len) {
                    value += cbuf[i]
                }
                val escapetString = value
                    .replace(">", "&gt;")
                    .replace("<", "&lt;")
                writer.write(escapetString)
            }

            @Throws(IOException::class)
            override fun flush() {
                writer.flush()
            }

            @Throws(IOException::class)
            override fun close() {
                writer.close()
            }
        }
    }

    override fun createEscapingWriterFor(out: OutputStream, enc: String): Writer {
        throw IllegalArgumentException("not supported")
    }
}
