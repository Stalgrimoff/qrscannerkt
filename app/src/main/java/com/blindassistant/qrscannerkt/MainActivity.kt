package com.blindassistant.qrscannerkt

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.sqlite.SQLiteDatabase
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.blindassistant.qrscannerkt.databinding.ActivityMainBinding
import com.google.gson.annotations.SerializedName
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.*
import java.io.File
import java.io.IOException
import java.sql.SQLException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors


private var Stopped: Boolean = false
private var Running: Boolean = false
private var AppId: String = "3257b0ae1f8d05fed50a757017a93688"
lateinit var mDB: SQLiteDatabase
private var mediaPlayer: MediaPlayer? = null
private var preInstalled = arrayOf("school10","bgitu")
val blockedArray = ArrayList<String>()
lateinit var mDBHelper: DatabaseHelper;
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
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            cameraExecutor.shutdown()
            mediaPlayer?.stop()
            mediaPlayer?.reset()
            Stopped = false;
            viewBinding.button.text = "Start"
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
                    PrintToast(it?.name.toString())
                    if(it?.name.toString() != "info") {
                        apiService.downloadFile(it.name.toString())
                    }
                } else {
                    PrintToast("CONNECTION FAILED")
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
                Manifest.permission.RECORD_AUDIO,
            ).apply {
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                    add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }.toTypedArray()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data);
        val uri: Uri? = data?.data
        val libraryManager = LibraryManager()
        libraryManager.newLibrary(uri)
        if(Running) { //restarting camera
            onStart(viewBinding.root)
            onStart(viewBinding.root)
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    fun onSettings(view: View) {
//        val SettingsIntent = Intent(this, SettingsActivity::class.java)
//        startActivity(SettingsIntent)

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
