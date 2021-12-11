package com.iot.wateranalyst.ui.main

interface DataUpdateListener {
    fun onDataUpdate(isDataReceived: Boolean, receivedWaterData: WaterData, rawData: ArrayList<Byte>)
}