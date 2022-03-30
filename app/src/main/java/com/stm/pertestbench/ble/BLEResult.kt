package com.stm.pertestbench.ble

/**
 * BLE Result used for BLE Queue System to determine if BLE operation was completed.
 *
 * @author Claudio Vertemara
 * @param id Result ID (UUID)
 * @param value Characteristic or Descriptor Value
 * @param status Integer Status
 * @see BLEManager
 * @see BLEManager.channel
 * @see BLEManager.scope
 * @see BLEManager.waitForResult
 */
data class BLEResult(
    val id: String,
    val value: ByteArray?,
    val status: Int
) {
    // AutoGenerated OverRide Functions for ByteArray
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as BLEResult

        if (id != other.id) return false
        if (value != null) {
            if (other.value == null) return false
            if (!value.contentEquals(other.value)) return false
        } else if (other.value != null) return false
        if (status != other.status) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + (value?.contentHashCode() ?: 0)
        result = 31 * result + status
        return result
    }
}
