package com.stm.pertestbench.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.stm.pertestbench.PERApplication.Companion.app
import com.stm.pertestbench.PERViewModel
import com.stm.pertestbench.activity.param.ParamInterface
import com.stm.pertestbench.activity.scan.ScanInterface
import com.stm.pertestbench.activity.scan.fragment.graph.GraphInterface
import com.stm.pertestbench.activity.scan.fragment.scan.ScanAdapter
import com.stm.pertestbench.extension.toHexString
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.trySendBlocking
import timber.log.Timber
import java.util.*
import java.util.concurrent.TimeUnit

const val ENABLE_BLUETOOTH_REQUEST_CODE = 1
private const val GATT_MAX_MTU_SIZE = 517
private const val CCC_DESCRIPTOR_UUID = "00002902-0000-1000-8000-00805f9b34fb"

/**
 * BLE Manager is a general manager object that interacts with Android's
 * Bluetooth API and is used in multiple ST applications. It handles all
 * BLE operations such as scanning, connecting, and characteristic read,
 * write, and notify functionality.
 *
 * @author Claudio Vertemara
 * @see BLEResult
 * @see BLETimeoutException
 * @see BluetoothGatt
 * @see BluetoothAdapter
 * @see BluetoothLeScanner
 */

@Suppress("unused")
@SuppressLint("NotifyDataSetChanged", "MissingPermission")
object BLEManager {

    private var viewModel = PERViewModel()
    var scanInterface: ScanInterface? = null
    var graphInterface: GraphInterface? = null
    var paramInterface: ParamInterface? = null

    /** List of Scanned BLE Devices */
    val scanResults = mutableListOf<ScanResult>()
    /** Selected Scan Result for Graph Fragment */
    var graphResult: ScanResult? = null

    var bGatt: BluetoothGatt? = null
    /** Scan Adapter for Scan RecyclerView */
    val scanAdapter = ScanAdapter(scanResults)

    // BLE Queue System (Coroutines)
    /** Channel for BLE Queue System */
    private val channel = Channel<BLEResult>()
    /** Coroutine Scope for BLE Queue System */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var isScanning = false
    private var isConnected = false
    var scanFilter = true

    val bAdapter: BluetoothAdapter by lazy {
        val bluetoothManager = app.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }

    private val bleScanner: BluetoothLeScanner by lazy {
        bAdapter.bluetoothLeScanner
    }

    /** Bluetooth 5 */

    /**
     * Checks device for Bluetooth 5 support and logs it.
     *
     * @see BluetoothAdapter.isLe2MPhySupported
     * @see BluetoothAdapter.isLeCodedPhySupported
     * @see BluetoothAdapter.isLeExtendedAdvertisingSupported
     * @see BluetoothAdapter.isLePeriodicAdvertisingSupported
     */
    @RequiresApi(Build.VERSION_CODES.O)
    fun checkBluetooth5Support() {
        Timber.i("LE 2M PHY Supported: ${bAdapter.isLe2MPhySupported}")
        Timber.i("LE Coded PHY Supported: ${bAdapter.isLeCodedPhySupported}")
        Timber.i("LE Extended Advertising Supported: ${bAdapter.isLeExtendedAdvertisingSupported}")
        Timber.i("LE Periodic Advertising Supported: ${bAdapter.isLePeriodicAdvertisingSupported}")
    }

    /** BLE Scan */

    /**
     * Checks & requests scan permissions or clears scan results list,
     * starts Bluetooth scan, and starts scan timer.
     *
     * @param context Activity Context
     * @see BluetoothLeScanner.startScan
     * @see PERViewModel.startScanTimer
     */
    @SuppressLint("ObsoleteSdkInt")
    fun startScan(context: Context) {
        // Start BLE Scan if Has Permissions
        if (!hasPermissions(context)) {
            scanInterface?.requestPermissions()
        } else if (!isScanning) {
            scanResults.clear()
            scanAdapter.notifyDataSetChanged()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                checkBluetooth5Support()
            }

            bleScanner.startScan(null, scanSettings, scanCallback)
            viewModel.startScanTimer()
            isScanning = true
            Timber.i("BLE Scan Started")
        }
    }

    /**
     * Stops Bluetooth scan and scan timer.
     *
     * @see BluetoothLeScanner.stopScan
     * @see PERViewModel.stopScanTimer
     */
    fun stopScan() {
        if (isScanning) {
            bleScanner.stopScan(scanCallback)
            viewModel.stopScanTimer()
            isScanning = false
            Timber.i("BLE Scan Stopped")
        }
    }

    // Set Scan Settings (Low Latency High Power Usage)
    private val scanSettings = ScanSettings.Builder()
        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
        .build()

    /**
     * Scan Result Callback
     */
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val index = scanResults.indexOfFirst { it.device.address == result.device.address }

            if (index != -1) { // Updates Existing Scan Result
                scanResults[index] = result
                scanAdapter.notifyItemChanged(index)

                // Update Graphs
                if (graphResult?.device?.address == result.device.address) {
                    graphInterface?.readData(result)
                }
            } else { // Adds New Scan Result
                with(result.device) {
                    Timber.i("Found BLE device! Name: ${name ?: "Unnamed"}, address: $address")
                }

                // Adds scanned device item to Recycler View if not filtered out
                if (!scanFilter || scanAdapter.filterComparison(result)) {
                    scanResults.add(result)
                    scanAdapter.notifyItemInserted(scanResults.size - 1)
                }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Timber.e("Scan Failed! Code: $errorCode")
        }
    }

    /** BLE Connection */

    /**
     * Stops Bluetooth scan and attempts connection to the
     * selected device.
     *
     * @param result Selected Scan Result Device
     */
    fun connect(result: ScanResult) {
        if (!isConnected) {
            stopScan()

            with(result.device) {
                connectGatt(app, false, gattCallback)
                Timber.i("Connecting to $address")
            }
        }
    }

    /**
     * Disconnects from device if connected to one.
     */
    fun disconnect() {
        if (isConnected) bGatt?.disconnect()
    }

    /**
     * Connection Callback
     */
    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            val deviceAddress = gatt.device.address

            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    isConnected = true
                    Timber.i("Successfully connected to $deviceAddress")

                    bGatt = gatt
                    scanInterface?.startIntent(gatt.device.name)

                    Handler(Looper.getMainLooper()).post {
                        bGatt!!.discoverServices()
                    }
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    isConnected = false
                    Timber.i("Successfully disconnected from $deviceAddress")
                    paramInterface?.finishActivity()
                    gatt.close()
                }
            } else {
                isConnected = false
                val message = "Connection Attempt Failed for $deviceAddress! Error: $status"
                Timber.e(message)
                scanInterface?.startToast(message)

                paramInterface?.finishActivity()
                gatt.close()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            with(gatt) {
                Timber.i("Discovered ${services.size} services for ${device.address}")
                printGattTable()

                scope.launch {
                    requestMTU(GATT_MAX_MTU_SIZE)
                }
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            Timber.i("ATT MTU changed to $mtu, Success: ${status == BluetoothGatt.GATT_SUCCESS}")
            channel.trySendBlocking(BLEResult("MTU", null, status))
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            with(characteristic) {
                when (status) {
                    BluetoothGatt.GATT_SUCCESS -> {
                        Timber.i("Read characteristic $uuid:\n${value.toHexString()}")
                    }
                    BluetoothGatt.GATT_READ_NOT_PERMITTED -> {
                        Timber.e("Read not permitted for $uuid!")
                    }
                    else -> {
                        Timber.e("Characteristic read failed for $uuid, Error: $status")
                    }
                }

                channel.trySendBlocking(BLEResult(characteristic.uuid.toString(), value, status))
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            with(characteristic) {
                when (status) {
                    BluetoothGatt.GATT_SUCCESS -> {
                        Timber.i("Wrote to characteristic ${this.uuid} | value: ${this.value?.toHexString()}")
                    }
                    BluetoothGatt.GATT_INVALID_ATTRIBUTE_LENGTH -> {
                        Timber.e("Write exceeded connectionInterface ATT MTU!")
                    }
                    BluetoothGatt.GATT_WRITE_NOT_PERMITTED -> {
                        Timber.e("Write not permitted for ${this.uuid}!")
                    }
                    else -> {
                        Timber.e("Characteristic write failed for ${this.uuid}, error: $status")
                    }
                }

                channel.trySendBlocking(BLEResult(characteristic.uuid.toString(), value, status))
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic
        ) {
            with(characteristic) {
                Timber.i("Characteristic ${this.uuid} changed | value: ${this.value?.toHexString()}")
            }
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt?,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            with (descriptor) {
                channel.trySendBlocking(BLEResult(uuid.toString(), value, status))
            }
        }
    } // End of Connection Callback

    /**
     * Logs UUIDs of available services & characteristics from Bluetooth GATT.
     */
    private fun BluetoothGatt.printGattTable() {
        if (services.isEmpty()) {
            Timber.i("No service and characteristic available, call discoverServices() first?")
            return
        }

        services.forEach { service ->
            val characteristicsTable = service.characteristics.joinToString(
                separator = "\n|--",
                prefix = "|--"
            ) { it.uuid.toString() }

            Timber.i("\nService: ${service.uuid}\nCharacteristics:\n$characteristicsTable")
        }
    }

    /**
     * Requests to change MTU size to given size.
     *
     * @param size MTU Size
     * @return BLE Result for BLE Queue System
     */
    suspend fun requestMTU(size: Int): BLEResult? {
        bGatt?.requestMtu(size)
        return waitForResult("MTU")
    }

    /** Characteristics (Read/Write) */

    /**
     * Gets a characteristic using a service & characteristic UUIDs.
     *
     * @param serviceUUIDString Service UUID
     * @param characteristicUUIDString Characteristic UUID
     * @return Bluetooth Characteristic
     */
    fun getCharacteristic(
        serviceUUIDString: String, characteristicUUIDString: String
    ): BluetoothGattCharacteristic? {
        val serviceUUID = UUID.fromString(serviceUUIDString)
        val characteristicUUID = UUID.fromString(characteristicUUIDString)

        return bGatt?.getService(serviceUUID)?.getCharacteristic(characteristicUUID)
    }

    /**
     * Reads from given characteristic.
     *
     * @param characteristic Bluetooth Characteristic
     * @return BLE Result for BLE Queue System
     */
    suspend fun readCharacteristic(characteristic: BluetoothGattCharacteristic?): BLEResult? {
        if (characteristic!= null && characteristic.isReadable()) {
            bGatt?.readCharacteristic(characteristic)
            return waitForResult(characteristic.uuid.toString())
        } else error("Characteristic ${characteristic?.uuid} cannot be read")
    }

    /**
     * Writes payload to given characteristic.
     *
     * @param characteristic Bluetooth Characteristic
     * @param payload Byte Array Message
     * @return BLE Result for BLE Queue System
     */
    suspend fun writeCharacteristic(characteristic: BluetoothGattCharacteristic?, payload: ByteArray): BLEResult? {
        val writeType = when {
            characteristic?.isWritable() == true -> BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            characteristic?.isWritableWithoutResponse() == true -> {
                BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            }
            else -> error("Characteristic ${characteristic?.uuid} cannot be written to")
        }

        bGatt?.let { gatt ->
            characteristic.writeType = writeType
            characteristic.value = payload
            gatt.writeCharacteristic(characteristic)
        } ?: error("Not connected to a BLE device!")
        return waitForResult(characteristic.uuid.toString())
    }

    /** Notifications / Indications */

    /**
     * Enables notifications on a given characteristic.
     *
     * @param characteristic Bluetooth Characteristic
     * @return BLE Result for BLE Queue System
     * @see writeDescriptor
     */
    suspend fun enableNotifications(characteristic: BluetoothGattCharacteristic?): BLEResult? {
        val cccdUuid = UUID.fromString(CCC_DESCRIPTOR_UUID)
        val payload = when {
            characteristic?.isIndicatable() == true -> BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
            characteristic?.isNotifiable() == true -> BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            else -> {
                Timber.e("${characteristic?.uuid} doesn't support notifications/indications")
                return null
            }
        }

        characteristic.getDescriptor(cccdUuid)?.let { cccDescriptor ->
            if (bGatt?.setCharacteristicNotification(characteristic, true) == false) {
                Timber.e("setCharacteristicNotification failed for ${characteristic.uuid}")
                return null
            }
            writeDescriptor(cccDescriptor, payload)
            return waitForResult(cccDescriptor.uuid.toString())
        } ?: Timber.e("${characteristic.uuid} doesn't contain the CCC descriptor!")
        return null
    }

    /**
     * Disables notifications on a given characteristic.
     *
     * @param characteristic Bluetooth Characteristic
     * @return BLE Result for BLE Queue System
     * @see writeDescriptor
     */
    suspend fun disableNotifications(characteristic: BluetoothGattCharacteristic?): BLEResult? {
        if (characteristic != null) {
            if (!characteristic.isNotifiable() && !characteristic.isIndicatable()) {
                Timber.e("${characteristic.uuid} doesn't support indications/notifications")
                return null
            }
        }

        val cccdUUID = UUID.fromString(CCC_DESCRIPTOR_UUID)
        characteristic?.getDescriptor(cccdUUID)?.let { cccDescriptor ->
            if (bGatt?.setCharacteristicNotification(characteristic, false) == false) {
                Timber.e("setCharacteristicNotification failed for ${characteristic.uuid}")
                return null
            }
            writeDescriptor(cccDescriptor, BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE)
            return waitForResult(cccDescriptor.uuid.toString())
        } ?: Timber.e("${characteristic?.uuid} doesn't contain the CCC descriptor")
        return null
    }

    /**
     * Writes payload to given descriptor.
     *
     * @param descriptor Bluetooth Descriptor
     * @param payload Byte Array Message
     */
    private fun writeDescriptor(descriptor: BluetoothGattDescriptor, payload: ByteArray) {
        bGatt?.let { gatt ->
            descriptor.value = payload
            gatt.writeDescriptor(descriptor)
        } ?: error("Not connected to a BLE device!")
    }

    /** Helper Functions */

    /**
     * Checks if all needed runtime permissions are granted.
     *
     * @param context Activity Context
     * @return True if All Permissions are Granted | False if At Least One Permission is Not Granted
     * @see hasLocationPermission
     * @see hasBluetoothPermission
     * @see PERViewModel.hasStoragePermission
     */
    fun hasPermissions(context: Context): Boolean {
        return hasLocationPermission(context) &&
            hasBluetoothPermission(context) &&
            viewModel.hasStoragePermission(context)
    }

    /**
     * Checks if the ACCESS_FINE_LOCATION runtime permission was granted.
     * Necessary for Android API 23 and above.
     *
     * @param context Activity Context
     * @return True if Permission Granted | False if Permission Not Granted
     */
    @SuppressLint("ObsoleteSdkInt")
    private fun hasLocationPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true

        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Checks if the BLUETOOTH_SCAN & BLUETOOTH_CONNECT runtime permissions
     * were granted. Necessary for Android API 31 and above.
     *
     * @param context Activity Context
     * @return True if Permission Granted | False if Permission Not Granted
     */
    private fun hasBluetoothPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true

        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.BLUETOOTH_SCAN
        ) == PackageManager.PERMISSION_GRANTED &&
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.BLUETOOTH_CONNECT
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Checks if Bluetooth Characteristic is readable.
     *
     * @return True if Readable | False if Not Readable
     * @see containsProperty
     */
    private fun BluetoothGattCharacteristic.isReadable(): Boolean =
        containsProperty(BluetoothGattCharacteristic.PROPERTY_READ)

    /**
     * Checks if Bluetooth Characteristic is writable.
     *
     * @return True if Writable | False if Not Writable
     * @see containsProperty
     */
    private fun BluetoothGattCharacteristic.isWritable(): Boolean =
        containsProperty(BluetoothGattCharacteristic.PROPERTY_WRITE)

    /**
     * Checks if Bluetooth Characteristic is writable without a response.
     *
     * @return True if Writable | False if Not Writable
     * @see containsProperty
     */
    private fun BluetoothGattCharacteristic.isWritableWithoutResponse(): Boolean =
        containsProperty(BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)

    /**
     * Checks if Bluetooth Characteristic is indicatable.
     *
     * @return True if Indicatable | False if Not Indicatable
     * @see containsProperty
     */
    private fun BluetoothGattCharacteristic.isIndicatable(): Boolean =
        containsProperty(BluetoothGattCharacteristic.PROPERTY_INDICATE)

    /**
     * Checks if Bluetooth Characteristic is notifiable.
     *
     * @return True if Notifiable | False if Not Notifiable
     * @see containsProperty
     */
    private fun BluetoothGattCharacteristic.isNotifiable(): Boolean =
        containsProperty(BluetoothGattCharacteristic.PROPERTY_NOTIFY)

    /**
     * Checks if Bluetooth Characteristic contains a property.
     *
     * @param property Property to Check
     * @return True if Contains Property | False if Not Contains Property
     * @see containsProperty
     */
    private fun BluetoothGattCharacteristic.containsProperty(property: Int): Boolean {
        return properties and property != 0
    }

    /**
     * Waits 5 seconds for Bluetooth operation result. Uses Kotlin
     * Coroutines to limit BLE operations to run one at a time.
     *
     * @param id Result ID (UUID)
     * @return BLE Result for BLE Queue System
     * @see channel
     * @see scope
     * @see BLEResult
     */
    private suspend fun waitForResult(id: String): BLEResult? {
        return withTimeoutOrNull(TimeUnit.SECONDS.toMillis(5)) {
            var bleResult: BLEResult = channel.receive()
            while (bleResult.id != id) {
                bleResult = channel.receive()
            }
            bleResult
        } ?: run {
            //throw BLETimeoutException("BLE Operation Timed Out!")
            Timber.e("BLE Operation Timed Out!")
            return null
        }
    }
}