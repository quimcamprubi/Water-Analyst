package com.iot.wateranalyst.ui.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData

class BLEFragmentViewModel(application: Application, ) : AndroidViewModel(application) {

    private val context = getApplication<Application>().applicationContext
    val isScanning = MutableLiveData<Boolean>(false)
    val isBluetoothConnected = MutableLiveData<Boolean>(false)

}
