package com.blindassistant.qrscannerkt


import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.Switch
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity


class SettingsActivity : AppCompatActivity() {
    @SuppressLint("UseSwitchCompatOrMaterialCode")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        val settings = getSharedPreferences("CameraSettings", MODE_PRIVATE)

        val torch = findViewById<Switch>(R.id.switch1)
        if(settings.getBoolean("torch",false)) {
            torch.toggle()
        }
        torch.setOnCheckedChangeListener { compoundButton, b ->
            if(compoundButton.isChecked) {
                settings.edit().let {
                    it.putBoolean("torch",true)
                    it.apply()
                }
            } else {
                settings.edit().let {
                    it.putBoolean("torch",false)
                    it.apply()
                }
            }}

        val preview = findViewById<Switch>(R.id.switch2)
        if(settings.getBoolean("preview",true)) {
            preview.toggle()
        }
        preview.setOnCheckedChangeListener { compoundButton, b ->
            if(compoundButton.isChecked) {
                settings.edit().let {
                    it.putBoolean("preview",true)
                    it.apply()
                }
            } else {
                settings.edit().let {
                    it.putBoolean("preview",false)
                    it.apply()
                }
            }}

        val theme = findViewById<Spinner>(R.id.spinner1)
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, this.resources.getStringArray(R.array.themes))
        theme.adapter = adapter
        theme.setSelection(settings.getInt("theme",0))
        theme.onItemSelectedListener = object : AdapterView.OnItemSelectedListener{
            override fun onNothingSelected(parent: AdapterView<*>?) {
            }

            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                settings.edit().let {
                    it.putInt("theme", theme.selectedItemPosition)
                    it.apply()
                }
            }
        }

        val filter = findViewById<Spinner>(R.id.spinner2)
        val filter_adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, this.resources.getStringArray(R.array.filters))
        filter.adapter = filter_adapter
        filter.setSelection(settings.getInt("filter",0))
        filter.onItemSelectedListener = object : AdapterView.OnItemSelectedListener{
            override fun onNothingSelected(parent: AdapterView<*>?) {
            }

            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                settings.edit().let {
                    it.putInt("filter", filter.selectedItemPosition)
                    it.apply()
                }
            }
        }

        val AF = findViewById<Spinner>(R.id.spinner3)
        val AF_adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, this.resources.getStringArray(R.array.AF))
        AF.adapter = AF_adapter
        AF.setSelection(settings.getInt("AF",0))
        AF.onItemSelectedListener = object : AdapterView.OnItemSelectedListener{
            override fun onNothingSelected(parent: AdapterView<*>?) {
            }

            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                settings.edit().let {
                    it.putInt("AF", AF.selectedItemPosition)
                    it.apply()
                }
            }
        }
    }

    fun onLibrary(view: View) {
        val LibraryIntent = Intent(this, LibraryActivity::class.java)
        startActivity(LibraryIntent)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data);
        val uri: Uri? = data?.data
        val libraryManager = LibraryManager()
        libraryManager.newLibrary(uri)
    }

    @RequiresApi(Build.VERSION_CODES.R)
    fun addLibrary(view: View) {
        if(!Environment.isExternalStorageManager()) {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
            val uri = Uri.fromParts(
                "package",
                packageName, null
            )
            intent.data = uri
            startActivity(intent)
        }
        if(Environment.isExternalStorageManager()) {
            val code: Int = 0;
            var chooseFile = Intent(Intent.ACTION_GET_CONTENT)
            chooseFile.type = "application/zip"
            chooseFile = Intent.createChooser(chooseFile, "Choose a file")
            startActivityForResult(chooseFile,code)
        }
    }

}