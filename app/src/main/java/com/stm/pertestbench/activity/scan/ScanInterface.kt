package com.stm.pertestbench.activity.scan

import androidx.fragment.app.Fragment

/**
 * Public interface for Scan Activity.
 *
 * @author Claudio Vertemara
 */

interface ScanInterface {
    /**
     * Prompts user to enable Bluetooth.
     */
    fun promptEnableBluetooth()
    /**
     * Requests runtime permissions based on android version.
     *
     * Android 31 & Above: ACCESS_FINE_LOCATION, BLUETOOTH_SCAN,
     * BLUETOOTH_CONNECT, & WRITE_EXTERNAL_STORAGE.
     *
     * Android 30 & Below: ACCESS_FINE_LOCATION & WRITE_EXTERNAL_STORAGE.
     */
    fun requestPermissions()
    /**
     * Switches to given fragment.
     *
     * @param fragment Fragment to Start
     */
    fun startFragment(fragment: Fragment)
    /**
     * Starts intent to go to Parameters Activity.
     *
     * @param deviceName Device Name brought to Parameters Activity
     */
    fun startIntent(deviceName: String)
    /**
     * Creates and shows a toast message.
     *
     * @param message String Message to Show
     */
    fun startToast(message: String)
}