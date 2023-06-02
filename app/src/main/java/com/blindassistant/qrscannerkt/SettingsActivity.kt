package com.blindassistant.qrscannerkt


import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
    }

    fun onExit(view: View) {
        this.finish()
    }

    fun onLibrary(view: View) {

    }
}