package com.stm.pertestbench.extension

import java.nio.ByteBuffer

/**
 * Converts a byte array to a hexadecimal string.
 *
 * @return Hexadecimal String
 * @see joinToString
 * @see String.format
 */
fun ByteArray.toHexString(): String =
    joinToString (separator = " ", prefix = "0x") { String.format("%02x", it) }

/**
 * Converts a byte array to float.
 *
 * @return Converted Float
 * @see ByteBuffer.wrap
 */
fun ByteArray.toFloat(): Float = ByteBuffer.wrap(this).float