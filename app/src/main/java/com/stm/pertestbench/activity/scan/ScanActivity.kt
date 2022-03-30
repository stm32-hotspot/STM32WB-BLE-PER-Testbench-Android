package com.stm.pertestbench.activity.scan

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import com.stm.pertestbench.BuildConfig
import com.stm.pertestbench.R
import com.stm.pertestbench.activity.param.ParamActivity
import com.stm.pertestbench.ble.BLEManager
import com.stm.pertestbench.ble.BLEManager.bAdapter
import com.stm.pertestbench.ble.ENABLE_BLUETOOTH_REQUEST_CODE
import com.stm.pertestbench.databinding.ActivityScanBinding

/**
 * Scan Activity prompts the user for runtime permissions and
 * contains the Scan Fragment & Graph Fragment.
 *
 * @author Claudio Vertemara
 */

class ScanActivity : AppCompatActivity(), ScanInterface {

    private lateinit var binding: ActivityScanBinding

    /**
     * Sets up activity, toolbar, and starts Bluetooth scan.
     *
     * @see BLEManager.startScan
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_scan)

        setSupportActionBar(binding.toolbar)

        BLEManager.scanInterface = this
        BLEManager.startScan(this)
    }

    /**
     * Prompts user to enable Bluetooth if not enabled and
     * starts Bluetooth scan.
     *
     * @see promptEnableBluetooth
     * @see BLEManager.startScan
     */
    override fun onResume() {
        super.onResume()

        if (!bAdapter.isEnabled) {
            promptEnableBluetooth()
        }
        BLEManager.startScan(this)
    }

    /**
     * Stops Bluetooth scan.
     *
     * @see BLEManager.stopScan
     */
    override fun onStop() {
        super.onStop()
        BLEManager.stopScan()
    }

    /** Permission & Bluetooth Requests */

    /**
     * Prompts user to enable Bluetooth.
     *
     * @see BluetoothAdapter.isEnabled
     */
    override fun promptEnableBluetooth() {
        if(!bAdapter.isEnabled){
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            ActivityCompat.startActivityForResult(
                this, enableBtIntent, ENABLE_BLUETOOTH_REQUEST_CODE, null
            )
        }
    }

    /**
     * Requests runtime permissions based on android version.
     *
     * Android 31 & Above: ACCESS_FINE_LOCATION, BLUETOOTH_SCAN,
     * BLUETOOTH_CONNECT, & WRITE_EXTERNAL_STORAGE.
     *
     * Android 30 & Below: ACCESS_FINE_LOCATION & WRITE_EXTERNAL_STORAGE.
     *
     * @see requestMultiplePermissions
     */
    override fun requestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requestMultiplePermissions.launch(arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ))
        } else {
            requestMultiplePermissions.launch(arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ))
        }
    }

    private val requestMultiplePermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}

    /**
     * Rerequests runtime permissions if not given by user or starts
     * bluetooth scan. Android limits rerequests to 2.
     *
     * @see BLEManager.hasPermissions
     * @see BLEManager.startScan
     * @see requestPermissions
     */
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        // Start BLE Scan if Permissions Granted
        if (BLEManager.hasPermissions(this)) {
            BLEManager.startScan(this)
        } else {
            requestPermissions()
        }
    }

    /** Toolbar Menu */

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_toolbar, menu)
        // Get Current App Version
        menu.findItem(R.id.appVersionItem).apply {
            title = "$title ${BuildConfig.VERSION_NAME}"
        }
        return true
    }

    /**
     * Item on toolbar was selected (tapped on).
     *
     * Scan Item: Starts bluetooth scan and closes Graph Fragment.
     *
     * @see BLEManager.startScan
     */
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.scanItem -> {
                BLEManager.startScan(this)
                BLEManager.graphInterface?.closeFragment()
                BLEManager.graphResult = null
            }
        }

        return false
    }

    /** Helper Functions */

    /**
     * Switches to given fragment.
     *
     * @param fragment Fragment to Start
     * @see getSupportFragmentManager
     */
    override fun startFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction().apply {
            replace(R.id.fragment, fragment)
            addToBackStack("fragment")
            commit()
        }
    }

    /**
     * Starts intent to go to Parameters Activity.
     *
     * @param deviceName Device Name brought to Parameters Activity
     * @see Intent
     * @see Intent.putExtra
     */
    override fun startIntent(deviceName: String) {
        Intent(this@ScanActivity, ParamActivity::class.java).apply {
            putExtra("deviceName", deviceName)
            startActivity(this)
        }
    }

    /**
     * Creates and shows a toast message.
     *
     * @param message String Message to Show
     * @see Toast.makeText
     */
    override fun startToast(message: String) {
        runOnUiThread {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }

}