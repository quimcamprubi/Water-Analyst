package com.iot.wateranalyst.ui.main

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Transformations
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.iot.wateranalyst.R

class PageViewModel : ViewModel() {

    private val _index = MutableLiveData<Int>()
    private val stupidVariable: Int = 0

    val text: LiveData<String> = when(_index.value) {
        1 -> R.string.tab_welcome_1.()
        else -> "luls"
    }

    fun setIndex(index: Int) {
        _index.value = index
    }
}