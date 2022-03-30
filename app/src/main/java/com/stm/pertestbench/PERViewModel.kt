package com.stm.pertestbench

import android.Manifest
import android.bluetooth.le.ScanResult
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.os.*
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import com.stm.pertestbench.ble.BLEManager
import com.stm.pertestbench.ble.BLEResult
import com.stm.pertestbench.extension.hexToByteArray
import java.io.File
import java.util.*

/**
 * Contains the app-specific code for writing to the characteristic, BLE
 * scan timer, creating & saving data files, and getting the current time.
 *
 * @author Claudio Vertemara
 */

class PERViewModel: ViewModel() {

    /**
     * Converts the given message into a byte array and writes it to
     * the fixed characteristic.
     *
     * @param message Hexadecimal String
     * @return BLE Result used for BLE Queue System
     * @see BLEManager.getCharacteristic
     * @see BLEManager.writeCharacteristic
     * @see hexToByteArray
     */
    suspend fun writeCharacteristic(message: String): BLEResult? {
        val characteristic = BLEManager.getCharacteristic(
            "0000fe40-cc7a-482a-984a-7f2ed5b3e58f",
            "0000fe41-8e22-4541-9d4c-21edae82ed19"
        )
        val byteMessage = message.hexToByteArray()

        if (characteristic != null) {
            return BLEManager.writeCharacteristic(characteristic, byteMessage)
        }
        return null
    }

    /**
     * Checks if WRITE_EXTERNAL_STORAGE permission was granted. Necessary for
     * Android API 28 and under.
     *
     * @param context Activity Context
     * @return True if Permission is Granted | False if Permission is Not Granted
     */
    fun hasStoragePermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) return true

        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
    }

    /** BLE Scan Timer */

    private var scanTimer: Timer? = null

    /**
     * Creates and starts a new timer that periodically checks the Scan
     * Results list and removes results that have stopped advertising.
     *
     * @see scanTimer
     * @see BLEManager.scanResults
     */
    fun startScanTimer() {
        val period = 3000L

        // Check Scan Results Every X Seconds
        scanTimer = Timer().apply {
            scheduleAtFixedRate(object : TimerTask() {
                override fun run() {
                    // Create Scan Results Copy
                    val resultsCopy = mutableListOf<ScanResult>()
                    resultsCopy.addAll(BLEManager.scanResults)

                    // Check Each Result in Copy List
                    for (result in resultsCopy) {
                        val timeStamp = SystemClock.elapsedRealtime() - (result.timestampNanos / 1000000)

                        // Remove Result if Not Advertising in the Last X Seconds
                        if (timeStamp > period) {
                            Handler(Looper.getMainLooper()).post {
                                val index = BLEManager.scanResults.indexOf(result)
                                BLEManager.scanResults.remove(result)
                                BLEManager.scanAdapter.notifyItemRemoved(index)
                            }
                        }
                    }
                }
            }, 0, period)
        }
    }

    /**
     * Cancels the Scan Timer.
     *
     * @see scanTimer
     */
    fun stopScanTimer() {
        scanTimer?.cancel()
    }

    /** Create & Save Files */

    // Data Strings to Write to File (New Method)
    /** Current Data String to Write to File (New Method) */
    var data: String = ""
    /** All Data String to Write to File (New Method) */
    var allData: String = ""
    /** Save Data Button Clicked Boolean */
    var saveDataClicked = false
    // First Write Booleans for Adding Labels Row (Old Method)
    /** First Time Writing Current Data (Old Method) */
    private var isFirstDataWrite = true
    /** First Time Writing All Data (Old Method) */
    private var isFirstAllDataWrite = true

    /**
     * Creates and saves a csv data file to the documents folder (new method).
     * Used for Android API 29 and above.
     *
     * Creates one of two possible files: PERTestData or PERAllTestData.
     *
     * @param context Activity Context
     * @param allData True = PERAllTestData | False = PERTestData
     * @see data
     * @see allData
     * @see saveDataClicked
     */
    fun saveFile(context: Context?, allData: Boolean) {
        val isThereData = data.isNotEmpty() || (this.allData.isNotEmpty() && saveDataClicked)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && isThereData) {
            val fileName = if (allData) "PERAllTestData" else "PERTestData"
            val address = BLEManager.graphResult?.device?.address?.filter { it != ':' }
            val labels = "Time, Distance (Meters), RSSI (dBm), PER (%)\n"
            val data = if (allData) this.allData else this.data

            val resolver = context?.contentResolver
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "$fileName ($address).csv")
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Documents")
            }
            val uri = resolver?.insert(MediaStore.Files.getContentUri("external"), contentValues)

            if (uri != null) {
                resolver.openOutputStream(uri).use {
                    it?.write((labels + data).toByteArray())
                    it?.close()
                }
            }

            // Reset Data String
            if (allData) this.allData = "" else this.data = ""
        }
    }

    /**
     * Creates a csv data file or appends to an existing file if it already exists in
     * the documents folder (old method). Used for Android API 28 and below.
     *
     * Creates one of two possible files: PERTestData or PERAllTestData.
     *
     * @param data String Data to Append to File
     * @param allData True = PERAllTestData | False = PERTestData
     * @see isFirstDataWrite
     * @see isFirstAllDataWrite
     */
    fun writeFile(data: String, allData: Boolean) {
        if (saveDataClicked) {
            val address = BLEManager.graphResult?.device?.address?.filter { it != ':' }
            val directory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            val fileName = if (allData) "PERAllTestData" else "PERTestData"
            val file = File(directory, "$fileName ($address).csv")
            file.createNewFile()

            if (isFirstDataWrite || (allData && isFirstAllDataWrite)) {
                file.appendText("Time, Distance (Meters), RSSI (dBm), PER (%)\n")
                if (allData) isFirstAllDataWrite = false else isFirstDataWrite = false
            }

            file.appendText(data)
        }
    }

    /** Time */

    /**
     * Gets the current time from a calendar instance.
     *
     * @return Time String in "HOUR:MIN:SEC" Format
     * @see timeFormat
     */
    fun getTime(): String {
        with(Calendar.getInstance()) {
            val hour = timeFormat(get(Calendar.HOUR_OF_DAY))
            val min = timeFormat(get(Calendar.MINUTE))
            val sec = timeFormat(get(Calendar.SECOND))

            return "$hour:$min:$sec"
        }
    }

    /**
     * Formats a time integer into a 2 digit string with a 0 in
     * front if value is below 10.
     *
     * (7 -> "07")
     *
     * @param time Time Integer
     * @return Formatted Time String
     * @see getTime
     */
    private fun timeFormat(time: Int): String {
        return time.toString().padStart(2, '0')
    }

}