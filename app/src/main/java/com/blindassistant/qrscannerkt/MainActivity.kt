package com.blindassistant.qrscannerkt

import android.Manifest
import android.annotation.SuppressLint
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.sqlite.SQLiteDatabase
import android.graphics.Rect
import android.hardware.camera2.CaptureRequest
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.Size
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.camera2.impl.Camera2ImplConfig
import androidx.camera.camera2.internal.Camera2CameraControlImpl
import androidx.camera.camera2.internal.compat.CameraCharacteristicsCompat
import androidx.camera.camera2.internal.compat.quirk.CamcorderProfileResolutionQuirk
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.blindassistant.qrscannerkt.databinding.ActivityMainBinding
import com.google.mlkit.common.model.LocalModel
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.DetectedObject
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.custom.CustomObjectDetectorOptions
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import kotlinx.coroutines.*
import java.io.File
import java.io.IOException
import java.sql.SQLException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.log

var defRes = Size(640,480)
private var Stopped: Boolean = false
private var Running: Boolean = false
private var AppId: String = "3257b0ae1f8d05fed50a757017a93688"
lateinit var mDB: SQLiteDatabase
private var mediaPlayer: MediaPlayer? = null
var preInstalled = arrayOf("school10","bgitu")

val blockedArray = ArrayList<String>()
lateinit var mDBHelper: DatabaseHelper;
@ExperimentalCamera2Interop @Suppress("NAME_SHADOWING")
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

    fun initConfig() {
        val settings = this.getSharedPreferences("CameraSettings", AppCompatActivity.MODE_PRIVATE)
        if (!settings.contains("AF")) {
            settings.edit().let {
                it.putInt("AF", 0)
                it.putInt("theme", 0)
                it.putInt("filter", 0)
                it.putBoolean("torch",false)
                it.putBoolean("preview",true)
                it.apply()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MainActivity.appContext = applicationContext
        initConfig()
        viewBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)
        viewBinding.viewFinder.visibility = View.INVISIBLE
        mDBHelper = DatabaseHelper(MainActivity.appContext)
        val path = this.getExternalFilesDir(null)
        val folder = File(path, "audio")
        if(!folder.exists()) {
            folder.mkdirs()
        }
        
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

    override fun onResume() {
        super.onResume()
        restartCamera()
        if (!getSharedPreferences("CameraSettings", AppCompatActivity.MODE_PRIVATE).getBoolean("preview",true)) {
            viewBinding.viewFinder.visibility = View.INVISIBLE
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
            viewBinding.button.text = resources.getText(R.string.stop)
            if (getSharedPreferences("CameraSettings", AppCompatActivity.MODE_PRIVATE).getBoolean("preview",true)) {
                viewBinding.viewFinder.visibility = View.VISIBLE
            }
            Running = true
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            cameraExecutor.shutdown()
            cameraExecutor.shutdownNow()
            mediaPlayer?.stop()
            mediaPlayer?.reset()
            Stopped = false;
            viewBinding.button.text = resources.getText(R.string.start)
            viewBinding.viewFinder.visibility = View.INVISIBLE
            Running = false
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
    class YourImageAnalyzer : ImageAnalysis.Analyzer {
        fun requestDB(DB: String) {
            val apiService = APIManager.RestApiService()
            val userInfo = APIManager.UserInfo(name = DB)
            apiService.requestInfo(userInfo) {
                println(it?.name.toString())
                if(it?.name != null) {
                    if(it?.name.toString() != "info") {
                        apiService.downloadFile(it.name.toString())
                    }
                } else {
                    PrintToast("CONNECTION FAILED")
                    blockedArray.remove(DB)
                }
            }
        }
        fun PrintToast(value: String) {
            Toast.makeText(
                MainActivity.appContext,
                value,
                Toast.LENGTH_SHORT
            ).show()
        }
        fun QRChecker(value: String) {
            var libraryName: String = "";
            var isFound: Boolean = false;
            val infoCursor = mDB.rawQuery("SELECT * FROM info",null)
            infoCursor.moveToFirst()
            while (!infoCursor.isAfterLast()) {
                if(infoCursor.getString(1) == value.split(" ")[1]) {
                    libraryName = infoCursor.getString(0)
                    isFound = true
                    break
                }
                infoCursor.moveToNext()
            }
            infoCursor.close()

            if(isFound) {
                isFound = false
                val cursor = mDB.rawQuery("SELECT * FROM $libraryName",null)
                cursor.moveToFirst()
                while (!cursor.isAfterLast()) {
                    if(cursor.getString(0) == value.split(" ")[2]) {
                        isFound = true;
                        if(preInstalled.contains(libraryName)) { //если найденв предустановленная библиотека
                            mediaPlayer = MediaPlayer.create(MainActivity.appContext, appContext.resources.getIdentifier(libraryName + "_" + cursor.getString(1), "raw", appContext.packageName))
                            mediaPlayer?.setOnPreparedListener {
                                mediaPlayer?.start()
                            }
                            mediaPlayer?.setOnCompletionListener {
                                Stopped = false
                            }
                        } else { // если найдена внешняя библиотека
                            mediaPlayer = MediaPlayer.create(MainActivity.appContext,
                                (appContext.getExternalFilesDir("audio").toString() + "/" + libraryName + "_" + cursor.getString(1) + ".mp3")?.toUri())
                            mediaPlayer?.setOnPreparedListener {
                                mediaPlayer?.start()
                            }
                            mediaPlayer?.setOnCompletionListener {
                                Stopped = false
                            }

                        }
                        PrintToast(cursor.getString(2))
                        break
                    }
                    cursor.moveToNext()
                }
                cursor.close()

                if(!isFound) {
                    PrintToast("NO MATCH")
                }
            } else {
                mediaPlayer = MediaPlayer.create(MainActivity.appContext, appContext.resources.getIdentifier("nolibrary", "raw", appContext.packageName))
                mediaPlayer?.setOnPreparedListener {
                    mediaPlayer?.start()
                }
                mediaPlayer?.setOnCompletionListener {
                    Stopped = false
                }
                PrintToast("Отсутствует библиотека")
                blockedArray.add(value.split(" ")[1])
                requestDB(value.split(" ")[1])
            }
        }
        override fun analyze(imageProxy: ImageProxy) {
            val mediaImage = imageProxy.image

            val test = mediaImage?.let { Rect(0, 0, it.width, it.height) }

            println(test)

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
                                if(rawValue!!.split(" ")[0] == AppId && !blockedArray.contains(
                                        rawValue.split(" ")[1])) {
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

    @SuppressLint("RestrictedApi")
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
                .setDefaultResolution(CamcorderProfileResolutionQuirk(CameraCharacteristicsCompat.toCameraCharacteristicsCompat(Camera2CameraInfo.extractCameraCharacteristics(cameraProvider.availableCameraInfos[0]))).supportedResolutions
                    .let {it[it.size/2]})
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, YourImageAnalyzer())
                }

            // Select back camera as a default
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            // CAMERA SETTINGS
            val configBuilder = Camera2ImplConfig.Builder()

            val settings = this.getSharedPreferences("CameraSettings", AppCompatActivity.MODE_PRIVATE)
            val AFPref: Int = settings.getInt("AF",0)
            val filterPref: Int = settings.getInt("filter",0)

            when (AFPref) {
                0 -> configBuilder.setCaptureRequestOption(
                    CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO)
                1 -> configBuilder.setCaptureRequestOption(
                    CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_MACRO)
                2 -> configBuilder.setCaptureRequestOption(
                    CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_OFF)
                else -> {
                    configBuilder.setCaptureRequestOption(
                        CaptureRequest.CONTROL_AF_MODE,
                        CaptureRequest.CONTROL_AF_MODE_AUTO)
                }
            }

            when (filterPref) {
                0 -> configBuilder.setCaptureRequestOption(
                    CaptureRequest.CONTROL_EFFECT_MODE,
                    CaptureRequest.CONTROL_EFFECT_MODE_OFF)
                1 -> configBuilder.setCaptureRequestOption(
                    CaptureRequest.CONTROL_EFFECT_MODE,
                    CaptureRequest.CONTROL_EFFECT_MODE_MONO)

            }

            configBuilder.setCaptureRequestOption(
                CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE,
                CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_ON
            )
            configBuilder.setCaptureRequestOption(
                CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE,
                CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_ON
            )
            configBuilder.setCaptureRequestOption(
                CaptureRequest.CONTROL_SCENE_MODE,
                CaptureRequest.CONTROL_SCENE_MODE_BARCODE
            )

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
                cameraProvider.unbindAll()
                if (settings.getBoolean("preview",true)) {
                    (cameraProvider.bindToLifecycle(
                        this, cameraSelector, preview, imageAnalyzer).cameraControl as Camera2CameraControlImpl).addInteropConfig(configBuilder.build())
                } else {
                    (cameraProvider.bindToLifecycle(
                        this, cameraSelector, imageAnalyzer).cameraControl as Camera2CameraControlImpl).addInteropConfig(configBuilder.build())
                }
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

    fun restartCamera() {
        if(Running) {
            onStart(viewBinding.root)
            onStart(viewBinding.root)
        }
    }
    companion object {
        lateinit var appContext: Context
        private const val TAG = "CameraXApp"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS =
            mutableListOf (
                Manifest.permission.CAMERA,
            ).apply {
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                    add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }.toTypedArray()
    }

    @RequiresApi(Build.VERSION_CODES.R) // это нужно исправить!!
    fun onSettings(view: View) {
        val SettingsIntent = Intent(this, SettingsActivity::class.java)
        startActivity(SettingsIntent)
    }
}
