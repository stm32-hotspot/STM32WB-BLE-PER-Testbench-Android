package com.stm.pertestbench.activity.scan.fragment.scan

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import com.stm.pertestbench.R
import com.stm.pertestbench.ble.BLEManager
import com.stm.pertestbench.databinding.FragmentScanBinding

/**
 * Scan Fragment contains recycler view of scanned BLE devices and
 * allows the user to connect to a device or view the Graph Fragment.
 *
 * @author Claudio Vertemara
 */

class ScanFragment : Fragment() {

    private lateinit var binding: FragmentScanBinding

    /**
     * Sets up fragment, recycler view, and filter check box.
     *
     * @see setupRecyclerView
     */
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = DataBindingUtil.inflate(
            inflater,
            R.layout.fragment_scan,
            container,
            false
        )

        setupRecyclerView()
        binding.filterCheckBox.isChecked = BLEManager.scanFilter

        return binding.root
    }

    /**
     * Sets up the recycler view for the list scanned of BLE devices.
     *
     * @see BLEManager.scanResults
     * @see RecyclerView
     */
    private fun setupRecyclerView() {
        with (binding) {
            adapter = BLEManager.scanAdapter

            scanRecyclerView.apply {
                layoutManager = LinearLayoutManager(
                    activity,
                    RecyclerView.VERTICAL,
                    false
                )
                isNestedScrollingEnabled = false
            }

            // Turns Off Update Animation
            val animator = scanRecyclerView.itemAnimator
            if (animator is SimpleItemAnimator) {
                animator.supportsChangeAnimations = false
            }
        }
    }

}