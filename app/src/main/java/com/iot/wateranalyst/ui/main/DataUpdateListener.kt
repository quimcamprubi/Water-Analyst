package com.iot.wateranalyst.ui.main

interface DataUpdateListener {
    fun onDataUpdate(isDataReceived: Boolean, waterData: WaterData, rawData: ArrayList<Byte>)
}