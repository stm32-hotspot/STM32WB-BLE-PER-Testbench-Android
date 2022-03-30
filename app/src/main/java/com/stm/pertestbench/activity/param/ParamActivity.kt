package com.stm.pertestbench.activity.param

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Spinner
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.widget.doAfterTextChanged
import androidx.databinding.DataBindingUtil
import com.stm.pertestbench.PERViewModel
import com.stm.pertestbench.R
import com.stm.pertestbench.ble.BLEManager
import com.stm.pertestbench.databinding.ActivityParamBinding
import com.stm.pertestbench.extension.hexToLittleEndian
import com.stm.pertestbench.extension.removeWhiteSpace
import com.stm.pertestbench.extension.toHexFormat
import com.stm.pertestbench.extension.toSubString
import kotlinx.coroutines.launch

/**
 * Parameters Activity allows the user to configure and
 * send parameters to the connected device.
 *
 * @author Claudio Vertemara
 * */

class ParamActivity : AppCompatActivity(), ParamInterface {

    private lateinit var binding: ActivityParamBinding
    private val viewModel: PERViewModel by viewModels()
    private val sharedPrefs by lazy { getSharedPreferences("params", MODE_PRIVATE) }

    /**
     * Sets up activity, toolbar, Spinners, EditTexts, and activity mode.
     *
     * @see setupSpinner
     * @see setupEditTexts
     * @see toggleMode
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_param)
        binding.activity = this
        BLEManager.paramInterface = this

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // Switch to RX Mode if RX Board Connected
        if(intent.getStringExtra("deviceName") == "DTM-RX") {
            binding.modeSwitch.isChecked = true
        }

        setupSpinner(binding.txFrequencySpinner)
        setupSpinner(binding.rxFrequencySpinner)
        setupEditTexts()
        toggleMode()
    }

    /**
     * Disconnects from device.
     *
     * @see BLEManager.disconnect
     */
    override fun onStop() {
        super.onStop()
        BLEManager.disconnect()
    }

    /**
     * Closes the activity and returns to Scan Activity.
     */
    override fun finishActivity() {
        finish()
    }

    /** Spinners */

    /**
     * Toggles activity mode (RX or TX) and hides/shows
     * views based on mode.
     */
    fun toggleMode() {
        with (binding) {
            if (modeSwitch.isChecked) { // RX Mode
                rx.visibility = View.VISIBLE
                tx.visibility = View.GONE
                modeSwitch.text = getString(R.string.rx)
                loadSelections()
            } else { // TX Mode
                tx.visibility = View.VISIBLE
                rx.visibility = View.GONE
                modeSwitch.text = getString(R.string.tx)
            }
        }
    }

    /**
     * Sets up given spinner with a list of frequencies.
     *
     * @param spinner Spinner to Setup
     * @see frequencyList
     */
    private fun setupSpinner(spinner: Spinner) {
        // Create List
        val list = frequencyList()

        // Add List to Spinner
        spinner.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_item, list
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
    }

    /**
     * Creates a list of frequencies for spinners.
     *
     * @return Array List of Frequencies
     * @see setupSpinner
     */
    private fun frequencyList(): ArrayList<String> {
        val list = arrayListOf<String>()

        for (i in 0..39) {
            if (i != 37 && i != 38 && i != 39) {
                var f = 2404 + i * 2
                if (i >= 11) f += 2
                list.add("$f MHz (Channel $i)")
            }
        }

        // Add ADV Channels
        list.add(0, "2402 MHz (Channel 37)")
        list.add(12, "2426 MHz (Channel 38)")
        list.add("2480 MHz (Channel 39)")

        return list
    }

    /**
     * Limits EditTexts allowed input range.
     *
     * Length of Data:    0 - 255
     *
     * Number of Packets: 1 - 10000
     */
    @SuppressLint("SetTextI18n")
    private fun setupEditTexts() {
        with (binding) {
            dataLengthEditText.doAfterTextChanged {
                val text = dataLengthEditText.text.toString()
                if (text.isNotEmpty()) {
                    if (text.toInt() < 0) dataLengthEditText.setText("0")
                    if (text.toInt() > 255) dataLengthEditText.setText("255")
                }
            }

            packetNumberEditText.doAfterTextChanged {
                val text = packetNumberEditText.text.toString()
                if (text.isNotEmpty()) {
                    if (text.toInt() < 1) packetNumberEditText.setText("1")
                    if (text.toInt() > 10000) packetNumberEditText.setText("10000")
                }
            }
        }
    }

    /**
     * Loads selected parameters into views if in RX mode.
     *
     * @see toggleMode
     */
    private fun loadSelections() {
        with (binding) {
            if (modeSwitch.isChecked) { // RX Mode
                txPowerSpinner.setSelection(sharedPrefs.getInt("power", 0))
                txFrequencySpinner.setSelection(sharedPrefs.getInt("frequency", 0))
                dataLengthEditText.setText(sharedPrefs.getString("dataLength", "37"))
                packetPayloadSpinner.setSelection(sharedPrefs.getInt("packetPayload", 0))
                txPhySpinner.setSelection(sharedPrefs.getInt("phy", 0))
                rxFrequencySpinner.setSelection(sharedPrefs.getInt("frequency", 0))
                rxPhySpinner.setSelection(sharedPrefs.getInt("phy", 0))
                packetNumberEditText.setText(sharedPrefs.getString("packetNumber", "1500"))
            }
        }
    }

    /** Button */

    /**
     * Saves selected parameters if in TX mode and writes the
     * parameters to the device.
     *
     * Configure Button was Clicked.
     *
     * @see toggleMode
     * @see writeParams
     */
    fun onButtonClick() {
        // Save Selected Parameters
        if (!binding.modeSwitch.isChecked) { // TX Mode
            sharedPrefs.edit {
                putInt("power", binding.txPowerSpinner.selectedItemPosition)
                putInt("frequency", binding.txFrequencySpinner.selectedItemPosition)
                putString("dataLength", binding.dataLengthEditText.text.toString())
                putInt("packetPayload", binding.packetPayloadSpinner.selectedItemPosition)
                putInt("phy", binding.txPhySpinner.selectedItemPosition)
                putString("packetNumber", binding.packetNumberEditText.text.toString())
            }
        }

        writeParams()
    }

    /**
     * Grabs selected parameters from views and writes them
     * to the connected device.
     *
     * RX Mode: TX & RX selected parameters are sent.
     *
     * TX Mode: Only TX selected parameters are sent.
     *
     * @see toggleMode
     * @see PERViewModel.writeCharacteristic
     */
    private fun writeParams() {
        with (binding) {
            val mode = if (modeSwitch.isChecked) "01" else "00"

            // TX Parameters
            val txPower = txPowerSpinner.selectedItem.toSubString(0, 2)
                .removeWhiteSpace().toHexFormat(1)
            val txFrequency = txFrequencySpinner.selectedItemPosition.toHexFormat(1)
            val dataLength = dataLengthEditText.text.toString().toHexFormat(1)
            val packetPayload = packetPayloadSpinner.selectedItem.toSubString(2, 4)
            val txPhy = txPhySpinner.selectedItem.toSubString(2, 4)

            // RX Parameters
            val rxFrequency = rxFrequencySpinner.selectedItemPosition.toHexFormat(1)
            val rxPhy = rxPhySpinner.selectedItem.toSubString(2, 4)
            val modulationIndex = modulationIndexSpinner.selectedItem.toSubString(2, 4)

            val packetNumber = packetNumberEditText.text.toString().toHexFormat(2).hexToLittleEndian()

            val message = if (modeSwitch.isChecked) { // RX Mode
                "${mode}$txPower$txFrequency$dataLength$packetPayload${txPhy}$rxFrequency$rxPhy$modulationIndex$packetNumber"
            } else { // TX Mode
                "$mode$txPower$txFrequency$dataLength$packetPayload${txPhy}000000$packetNumber"
            }

            BLEManager.scope.launch {
                viewModel.writeCharacteristic(message)
            }
        }
    }

}