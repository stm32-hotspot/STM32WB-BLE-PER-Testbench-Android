package com.stm.pertestbench.extension

/**
 * Converts integer into a formatted hexadecimal string.
 *
 * @param numByte Number of Bytes
 * @return Formatted Hexadecimal String
 * @see toString
 * @see padStart
 */
fun Int.toHexFormat(numByte: Int): String {
    return toString(16).padStart(numByte * 2, '0')
}