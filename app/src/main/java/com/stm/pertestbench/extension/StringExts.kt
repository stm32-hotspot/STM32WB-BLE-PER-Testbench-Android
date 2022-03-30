@file:Suppress("unused")

package com.stm.pertestbench.extension

/**
 * Converts a hexadecimal string into a ascii string.
 *
 * @return ASCII String
 * @see chunked
 * @see map
 * @see toInt
 * @see toCharArray
 */
fun String.hexToASCII(): String {
    val charList = chunked(2).map { it.toInt(16).toChar() }
    return String(charList.toCharArray())
}

/**
 * Converts hexadecimal string into a byte array.
 *
 * https://stackoverflow.com/questions/66613717/kotlin-convert-hex-string-to-bytearray
 *
 * @return Byte Array
 * @see chunked
 * @see map
 * @see toInt
 * @see toByte
 * @see toByteArray
 */
fun String.hexToByteArray(): ByteArray {
    return chunked(2)
        .map { it.toInt(16).toByte() }
        .toByteArray()
}

/**
 * Converts string integer into a formatted hexadecimal string.
 *
 * @param numByte Number of Bytes
 * @return Formatted Hexadecimal String
 * @see toInt
 * @see toString
 * @see padStart
 */
fun String.toHexFormat(numByte: Int): String {
    return toInt().toString(16).padStart(numByte * 2, '0')
}

/**
 * Removes white space (spaces) from a string.
 *
 * @return String without White Space
 * @see filter
 * @see isWhitespace
 */
fun String.removeWhiteSpace(): String {
    return filter { !it.isWhitespace() }
}

/**
 * Flips hexadecimal string to little endian.
 *
 * @return Little Endian Hexadecimal String
 * @see hexToBigEndian
 */
fun String.hexToLittleEndian(): String {
    var littleEndian = ""
    for (i in (length - 1) downTo 0 step 2) {
        littleEndian += (this[i - 1] + this[i].toString())
    }
    return littleEndian
}

/**
 * Flips hexadecimal string to big endian.
 *
 * @return Big Endian Hexadecimal String
 * @see hexToLittleEndian
 */
fun String.hexToBigEndian(): String {
    return this.hexToLittleEndian()
}