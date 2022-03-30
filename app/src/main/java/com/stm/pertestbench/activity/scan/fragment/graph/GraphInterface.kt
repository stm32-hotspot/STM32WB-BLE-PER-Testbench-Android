package com.stm.pertestbench.activity.scan.fragment.graph

import android.bluetooth.le.ScanResult

/**
 * Public interface for Graph Fragment.
 *
 * @author Claudio Vertemara
 */

interface GraphInterface {
    /**
     * Reads and converts the advertising data into live data values.
     *
     * @param result Scan Result from Scan Call Back
     */
    fun readData(result: ScanResult)
    /**
     * Closes Graph Fragment and returns to Scan Fragment.
     */
    fun closeFragment()
}