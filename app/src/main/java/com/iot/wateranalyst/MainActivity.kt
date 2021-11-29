package com.iot.wateranalyst

import android.content.res.Configuration
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager.widget.ViewPager
import com.google.android.material.tabs.TabLayout
import com.iot.wateranalyst.databinding.ActivityMainBinding
import com.iot.wateranalyst.ui.main.DataUpdateListener
import com.iot.wateranalyst.ui.main.SectionsPagerAdapter
import com.iot.wateranalyst.ui.main.WaterData
import java.util.*


class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private var isDarkMode = false
    var rawList = arrayListOf<Byte>()
    private lateinit var viewPager: ViewPager
    private var listeners = mutableListOf<DataUpdateListener>()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isDarkMode = when (this.resources.configuration.uiMode.and(
            Configuration.UI_MODE_NIGHT_MASK)) {
            Configuration.UI_MODE_NIGHT_YES -> true
            else -> false
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val sectionsPagerAdapter = SectionsPagerAdapter(this, supportFragmentManager, isDarkMode)
        viewPager = binding.viewPager
        viewPager.adapter = sectionsPagerAdapter
        val tabs: TabLayout = binding.tabs
        tabs.setupWithViewPager(viewPager)

    }

    override fun onResume() {
        isDarkMode = when (this.resources.configuration.uiMode.and(
            Configuration.UI_MODE_NIGHT_MASK)) {
            Configuration.UI_MODE_NIGHT_YES -> true
            else -> false
        }
        super.onResume()
    }

    override fun onRestart() {
        isDarkMode = when (this.resources.configuration.uiMode.and(
            Configuration.UI_MODE_NIGHT_MASK)) {
            Configuration.UI_MODE_NIGHT_YES -> true
            else -> false
        }
        super.onRestart()
    }

    fun nextFragment(componentList: ArrayList<Byte>) {
        rawList = componentList
        val waterData = rawList.toByteArray().decodeToString().toWaterData()
        for (listener in listeners) {
            listener.onDataUpdate(isDataReceived(), waterData, rawList)
        }
        viewPager.currentItem = viewPager.currentItem + 1
    }

    fun isDataReceived() = rawList.size > 0

    @Synchronized
    fun registerDataUpdateListener(listener: DataUpdateListener) {
        listeners.add(listener)
    }

    @Synchronized
    fun unregisterDataUpdateListener(listener: DataUpdateListener) {
        listeners.remove(listener)
    }

    fun String.toWaterData(): WaterData {
        val strs = this.split(";")
        strs.dropLast(1)
        return WaterData(strs.get(0).toFloat(), strs.get(1).toFloat(), strs.get(2).toFloat(), strs.get(3).toFloat(),
            strs.get(4).toFloat(), strs.get(5).toFloat(), strs.get(6).toFloat(), strs.get(7).toFloat(),
            strs.get(8).toFloat())
    }
}