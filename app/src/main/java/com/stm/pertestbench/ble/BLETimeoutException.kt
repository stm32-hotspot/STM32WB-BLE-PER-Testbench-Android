package com.stm.pertestbench.ble

/**
 * Custom timeout exception for BLE Manager.
 *
 * @author Claudio Vertemara
 * @param message String Error Message
 * @see Exception
 * @see BLEManager
 */
class BLETimeoutException(message:String): Exception(message)