package com.stm.pertestbench.databinding

import androidx.databinding.BindingAdapter
import androidx.recyclerview.widget.RecyclerView

/**
 * Adds custom data binding functionality.
 *
 * @author Minku Yeo
 */
object DataBinding {

    /**
     * Binds an adapter to the recycler view.
     *
     * @param adapter Recycler View Adapter
     */
    @JvmStatic
    @BindingAdapter("recyclerViewAdapter")
    fun RecyclerView.bindRecyclerViewAdapter(adapter: RecyclerView.Adapter<*>) {
        this.adapter = adapter
    }

}