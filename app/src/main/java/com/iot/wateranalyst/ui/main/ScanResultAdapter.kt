package com.iot.wateranalyst.ui.main

import android.bluetooth.le.ScanResult
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.iot.wateranalyst.R
import kotlinx.android.synthetic.main.scanned_device_row.view.*

class ScanResultAdapter(
    private val items: List<ScanResult>,
    private val isDarkMode: Boolean,
    private val onClickListener: ((device: ScanResult) -> Unit)
) : RecyclerView.Adapter<ScanResultAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater
            .from(parent.context)
            .inflate(R.layout.scanned_device_row, parent, false)
        return ViewHolder(view, onClickListener, isDarkMode)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.bind(item)
    }

    class ViewHolder(
        private val view: View,
        private val onClickListener: ((device: ScanResult) -> Unit),
        private val isDarkMode: Boolean = false
    ) : RecyclerView.ViewHolder(view) {

        fun bind(result: ScanResult) {
            val context = view.context
            if (isDarkMode) view.cardView.setCardBackgroundColor(context.getColor(R.color.very_dark_gray)) else view.cardView.setCardBackgroundColor(context.getColor(R.color.very_light_gray))
            view.name.text = result.device.name ?: "Unnamed"
            view.mac_address.text = result.device.address
            view.signal_strength.text = "${result.rssi} dBm"
            //view.setOnClickListener { onClickListener.invoke(result) }
        }
    }
}