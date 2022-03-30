package com.stm.pertestbench.extension

/**
 * Converts object to a substring.
 *
 * @param startIndex Start of SubString
 * @param endIndex End of SubString
 * @return Substring
 * @see toString
 * @see substring
 */
fun Any.toSubString(startIndex: Int, endIndex: Int): String {
    return toString().substring(startIndex, endIndex)
}