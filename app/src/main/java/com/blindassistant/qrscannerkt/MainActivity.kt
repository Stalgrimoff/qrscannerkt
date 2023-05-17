package com.blindassistant.qrscannerkt

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.sqlite.SQLiteDatabase
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.blindassistant.qrscannerkt.databinding.ActivityMainBinding
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.*
import java.io.IOException
import java.sql.SQLException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors


private var Stopped: Boolean = false
private var Running: Boolean = false
private var AppId: String = "3257b0ae1f8d05fed50a757017a93688"
lateinit var mDB: SQLiteDatabase
private var mediaPlayer: MediaPlayer? = null
@Suppress("NAME_SHADOWING")
@ExperimentalGetImage class MainActivity : AppCompatActivity() {
    private lateinit var viewBinding: ActivityMainBinding
    private lateinit var cameraExecutor: ExecutorService
    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults:
        IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(this,
                    "Permissions not granted by the user.",
                    Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MainActivity.appContext = applicationContext
        viewBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)
        viewBinding.viewFinder.visibility = View.INVISIBLE
        val mDBHelper = DatabaseHelper(MainActivity.appContext)

        try {
            mDBHelper.updateDataBase()
        } catch (MIOException: IOException) {
            throw Error("UnableToUpdateDB")
        }
        try {
            mDB = mDBHelper.writableDatabase
        } catch (mSQLException: SQLException) {
            throw mSQLException
        }
    }
    fun onStart(view: View) {
        if(!Running) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                ActivityCompat.requestPermissions(
                    this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
            }
            cameraExecutor = Executors.newSingleThreadExecutor()
            viewBinding.button.text = "Stop"
            viewBinding.viewFinder.visibility = View.VISIBLE
            Running = true

        } else {
            cameraExecutor.shutdown()
            mediaPlayer?.stop()
            mediaPlayer?.reset()
            Stopped = false;
            viewBinding.button.text = "Start"
            viewBinding.viewFinder.visibility = View.INVISIBLE
            Running = false
        }

    }
    class YourImageAnalyzer : ImageAnalysis.Analyzer {
        fun PrintToast(value: String) {
            Toast.makeText(
                MainActivity.appContext,
                value,
                Toast.LENGTH_SHORT
            ).show()
        }
        fun QRChecker(value: String) {
            if(value.split(" ")[1] == "5e07052733fc1343f5f6ea2f9e9b480b") {
                val cursor = mDB.rawQuery("SELECT * FROM school10",null)
                cursor.moveToFirst()
                while (!cursor.isAfterLast()) {
                    if(cursor.getString(0) == value.split(" ")[2]) {
                        mediaPlayer = MediaPlayer.create(MainActivity.appContext, appContext.resources.getIdentifier(cursor.getString(1), "raw", appContext.packageName))
                        mediaPlayer?.setOnPreparedListener {
                            Toast.makeText(
                                MainActivity.appContext,
                                "READY TO GO",
                                Toast.LENGTH_SHORT
                            ).show()
                            mediaPlayer?.start()
                        }
                        mediaPlayer?.setOnCompletionListener {
                            Stopped = false
                        }
                        PrintToast(cursor.getString(2))
                        break
                    }
                    cursor.moveToNext()
                }
                cursor.close()
            }
        }
        override fun analyze(imageProxy: ImageProxy) {
            val mediaImage = imageProxy.image
            if (mediaImage != null) {
                val image =
                    InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                val options = BarcodeScannerOptions.Builder()
                    .setBarcodeFormats(
                        Barcode.FORMAT_QR_CODE
                    )
                    .build()
                val scanner = BarcodeScanning.getClient(options)
                if (!Stopped) {
                    scanner.process(image)
                        .addOnSuccessListener { barcodes ->
                            for (barcode in barcodes) {
                                val rawValue = barcode.rawValue
                                if(rawValue!!.split(" ")[0] == AppId) {
                                    QRChecker(rawValue)
                                } else {
                                    Toast.makeText(
                                        MainActivity.appContext,
                                        "WRONG QR CODE",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    Handler(Looper.getMainLooper()).postDelayed(
                                        {
                                            Stopped = false
                                        },
                                        2000 // value in milliseconds
                                    )
                                }
                                Stopped = true
                                barcodes.clear()
                            }
                        }
                        .addOnFailureListener {
                        }
                        .addOnCompleteListener {
                            imageProxy.close()
                        }
                } else {
                    imageProxy.close()
                }
            }
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            // Used to bind the lifecycle of cameras to the lifecycle owner
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // Preview
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(viewBinding.viewFinder.surfaceProvider)
                }

            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, YourImageAnalyzer())
                }

            // Select back camera as a default
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
            cameraProviderFuture.addListener(Runnable {
                val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder()
                    .build()
                    .also {
                        it.setSurfaceProvider(viewBinding.viewFinder.surfaceProvider)

                    }
            }, ContextCompat.getMainExecutor(this))

            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()

                // Bind use cases to camera
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageAnalyzer)
            } catch(exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    companion object {
        lateinit var appContext: Context
        private const val TAG = "CameraXApp"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS =
            mutableListOf (
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO
            ).apply {
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                    add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }.toTypedArray()
    }

    fun onSettings(view: View) {
        val SettingsIntent = Intent(this, SettingsActivity::class.java)
        startActivity(SettingsIntent)
    }


}
