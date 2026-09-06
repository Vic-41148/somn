package dev.vic41148.somn.core.data.backup

import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream

/**
 * Counting stream that throws once more than [maxBytes] are read. Every network- or
 * document-provided body in this app passes through one: the updater already caps its
 * reads, and the NAS listing, CSV import, and backup-restore paths share this helper
 * so the next ingestion point cannot silently reintroduce an unbounded read.
 */
class BoundedInputStream(
    wrapped: InputStream,
    private val maxBytes: Long
) : FilterInputStream(wrapped) {

    private var total = 0L

    private fun count(read: Int): Int {
        if (read == -1) return -1
        total += read
        if (total > maxBytes) throw IOException("Input exceeded $maxBytes bytes")
        return read
    }

    override fun read(): Int = count(super.read())

    override fun read(b: ByteArray, off: Int, len: Int): Int =
        count(super.read(b, off, len))
}

/**
 * Reads this stream fully as text, failing past [maxBytes]. For CSV/XML ingestion where
 * the producer (NAS server, document picker, backup zip) is not trusted to be small.
 */
fun InputStream.readBoundedText(maxBytes: Long, charset: java.nio.charset.Charset): String {
    return BoundedInputStream(this, maxBytes).bufferedReader(charset).use { it.readText() }
}

/** CSV import cap: Sleep-as-Android exports are kilobytes; anything past this is hostile. */
const val MAX_CSV_IMPORT_BYTES = 8L * 1024 * 1024
