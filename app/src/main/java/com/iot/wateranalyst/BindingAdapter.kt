package com.iot.wateranalyst

import android.view.View
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.databinding.BindingAdapter

@BindingAdapter("android:visibility")
fun setVisibility(view: View, value: Boolean) {
    view.visibility = if (value) View.VISIBLE else View.GONE
}

@BindingAdapter("android:setTextColor")
fun setTextColor(textView: TextView, value: Double) {
    if (value < 0.3) {
        textView.setTextColor(ContextCompat.getColor(textView.context, R.color.red))
    } else if (value < 0.5) {
        textView.setTextColor(ContextCompat.getColor(textView.context, R.color.orange))
    } else if (value < 0.7) {
        textView.setTextColor(ContextCompat.getColor(textView.context, R.color.yellow))
    } else {
        textView.setTextColor(ContextCompat.getColor(textView.context, R.color.green))
    }
}

@BindingAdapter("android:setTextColorWithMessage")
fun setTextColorWithMessage(textView: TextView, waterQuality: String?) {
    waterQuality?.let {
        when (waterQuality) {
            "Very Bad" -> textView.setTextColor(ContextCompat.getColor(textView.context, R.color.red))
            "Bad" -> textView.setTextColor(ContextCompat.getColor(textView.context, R.color.orange))
            "Normal" -> textView.setTextColor(ContextCompat.getColor(textView.context, R.color.yellow))
            else -> textView.setTextColor(ContextCompat.getColor(textView.context, R.color.green))
        }
    }
}