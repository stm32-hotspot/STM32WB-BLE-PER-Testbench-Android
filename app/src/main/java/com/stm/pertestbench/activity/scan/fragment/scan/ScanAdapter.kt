package com.stm.pertestbench.activity.scan.fragment.scan

import android.annotation.SuppressLint
import android.bluetooth.le.ScanResult
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import androidx.databinding.DataBindingUtil
import androidx.recyclerview.widget.RecyclerView
import com.stm.pertestbench.R
import com.stm.pertestbench.activity.scan.fragment.graph.GraphFragment
import com.stm.pertestbench.ble.BLEManager
import com.stm.pertestbench.databinding.RowScanResultBinding

/**
 *  Scan Adapter for the scan recycler view.
 *
 *  @author Claudio Vertemara
 *  @param scanResults List of Scan Results
 */

@SuppressLint("NotifyDataSetChanged", "MissingPermission")
class ScanAdapter (
    private val scanResults: List<ScanResult>
) : RecyclerView.Adapter<ScanAdapter.ViewHolder>()  {

    /** Copy of Scan Results List */
    private val resultsCopy: ArrayList<ScanResult> = arrayListOf()

    /**
     * View Holder for scan recycler view and sets up button listeners.
     */
    inner class ViewHolder(val binding: RowScanResultBinding) : RecyclerView.ViewHolder(binding.root) {
        init {
            binding.connectButton.setOnClickListener {
                val result = scanResults[bindingAdapterPosition]
                BLEManager.connect(result)
            }
            binding.graphButton.setOnClickListener {
                BLEManager.graphResult = scanResults[bindingAdapterPosition]
                BLEManager.scanInterface?.startFragment(GraphFragment())
            }
        }
    }

    /**
     * Sets up View Holder.
     * */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = DataBindingUtil.inflate<RowScanResultBinding>(
            inflater,
            R.layout.row_scan_result,
            parent,
            false
        )
        return ViewHolder(binding)
    }

    /**
     * Binds Scan Result information to View Holder.
     */
    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val result = scanResults[position]

        with(holder.binding) {
            deviceName.text = result.device.name ?: "Unnamed"
            macAddress.text = result.device.address
            signalStrength.text = "${result.rssi} dBm"

            connectButton.visibility = if (!result.isConnectable) View.GONE else View.VISIBLE
            graphButton.visibility = if (!result.isConnectable && filterComparison(result))
                View.VISIBLE else View.GONE
        }
    }

    /** Total number of scan results in the list. */
    override fun getItemCount() = scanResults.size

    /** Filter */

    /**
     * Sets scan filter based on if checkbox is checked.
     *
     * @param checkBox Scan Filter Checkbox
     * @see BLEManager.scanFilter
     */
    fun onCheckBoxChecked(checkBox: CheckBox) {
        BLEManager.scanFilter = checkBox.isChecked
        if (checkBox.isChecked) filter()
    }

    /**
     * Filters Scan Results list by device name.
     *
     * @see BLEManager.scanResults
     * @see filterComparison
     */
    private fun filter() {
        resultsCopy.clear()
        resultsCopy.addAll(scanResults)
        BLEManager.scanResults.clear()

        for (result in resultsCopy) {
            if (filterComparison(result)) {
                BLEManager.scanResults.add(result)
            }
        }

        notifyDataSetChanged()
    }

    /**
     * Comparison for the scan filter.
     *
     * @param result Scan Result
     * @return True if Result Device Name Contains "DTM" | False if it Does Not
     * @see filter
     * @see ScanResult
     * @see scanResults
     */
    fun filterComparison(result: ScanResult): Boolean {
        val name = result.device.name
        return name != null && name.startsWith("DTM")
    }

}