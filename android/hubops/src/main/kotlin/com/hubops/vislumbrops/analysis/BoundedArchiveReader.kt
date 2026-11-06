package com.hubops.vislumbrops.analysis

import java.io.ByteArrayOutputStream
import java.io.InputStream

internal object BoundedArchiveReader {
    fun read(input: InputStream, maxBytes: Int): ByteArray? {
        val output = ByteArrayOutputStream(minOf(maxBytes, 64 * 1024))
        val buffer = ByteArray(16 * 1024)
        var total = 0
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            if (total > maxBytes) return null
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }
}
