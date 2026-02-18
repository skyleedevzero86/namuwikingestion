package com.sleekydz86.namuwikingestion.infrastructure.parquet

import org.apache.parquet.io.InputFile
import org.apache.parquet.io.SeekableInputStream
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer

class LocalInputFile(private val file: File) : InputFile {

    override fun getLength(): Long = file.length()

    override fun newStream(): SeekableInputStream = RAFSeekableInputStream(RandomAccessFile(file, "r"))
}

private class RAFSeekableInputStream(private val raf: RandomAccessFile) : SeekableInputStream() {

    override fun getPos(): Long = raf.filePointer

    override fun seek(newPos: Long) {
        raf.seek(newPos)
    }

    override fun read(): Int = raf.read()

    override fun read(b: ByteArray): Int = raf.read(b)

    override fun read(b: ByteArray, off: Int, len: Int): Int = raf.read(b, off, len)

    override fun read(buf: ByteBuffer): Int = raf.channel.read(buf)

    override fun readFully(bytes: ByteArray) {
        raf.readFully(bytes)
    }

    override fun readFully(bytes: ByteArray, offset: Int, len: Int) {
        raf.readFully(bytes, offset, len)
    }

    override fun readFully(buf: ByteBuffer) {
        val pos = buf.position()
        val len = buf.remaining()
        if (len <= 0) return
        val arr = ByteArray(len)
        raf.readFully(arr)
        buf.put(arr)
    }

    override fun close() {
        raf.close()
    }
}
