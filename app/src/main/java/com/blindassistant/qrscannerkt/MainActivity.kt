package com.blindassistant.qrscannerkt

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.annotation.RequiresApi
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
    lateinit var mDBHelper: DatabaseHelper;
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
                        Toast.makeText(
                            MainActivity.appContext,
                            cursor.getString(1),
                            Toast.LENGTH_SHORT
                        ).show()

                        isFound = true;
                        mediaPlayer = MediaPlayer.create(MainActivity.appContext, appContext.resources.getIdentifier(libraryName + "_" + cursor.getString(1), "raw", appContext.packageName))
                        mediaPlayer?.setOnPreparedListener {
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

                if(!isFound) {
                    Toast.makeText(
                        MainActivity.appContext,
                        "NO MATCH",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                Handler(Looper.getMainLooper()).postDelayed(
                    {
                        Stopped = false
                    },
                    2000
                )
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
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ).apply {
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                    add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }.toTypedArray()
    }

    fun getPath(context: Context, uri: Uri): String? {
        val isKitKatorAbove = Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT

        // DocumentProvider
        if (isKitKatorAbove && DocumentsContract.isDocumentUri(context, uri)) {
            // ExternalStorageProvider
            if (isExternalStorageDocument(uri)) {
                val docId = DocumentsContract.getDocumentId(uri)
                val split = docId.split(":".toRegex()).toTypedArray()
                val type = split[0]
                if ("primary".equals(type, ignoreCase = true)) {
                    return Environment.getExternalStorageDirectory().toString() + "/" + split[1]
                }

            } else if (isDownloadsDocument(uri)) {
                val id = DocumentsContract.getDocumentId(uri)
                val contentUri = ContentUris.withAppendedId(Uri.parse("content://downloads/public_downloads"), java.lang.Long.valueOf(id))
                return getDataColumn(context, contentUri, null, null)
            } else if (isMediaDocument(uri)) {
                val docId = DocumentsContract.getDocumentId(uri)
                val split = docId.split(":".toRegex()).toTypedArray()
                val type = split[0]
                var contentUri: Uri? = null
                if ("image" == type) {
                    contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                } else if ("video" == type) {
                    contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                } else if ("audio" == type) {
                    contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                }
                val selection = "_id=?"
                val selectionArgs = arrayOf(split[1])
                return getDataColumn(context, contentUri, selection, selectionArgs)
            }
        } else if ("content".equals(uri.scheme, ignoreCase = true)) {
            return getDataColumn(context, uri, null, null)
        } else if ("file".equals(uri.scheme, ignoreCase = true)) {
            return uri.path
        }
        return null
    }

    fun getDataColumn(context: Context, uri: Uri?, selection: String?, selectionArgs: Array<String>?): String? {
        var cursor: Cursor? = null
        val column = "_data"
        val projection = arrayOf(column)
        try {
            cursor = context.getContentResolver().query(uri!!, projection, selection, selectionArgs,null)
            if (cursor != null && cursor.moveToFirst()) {
                val column_index: Int = cursor.getColumnIndexOrThrow(column)
                return cursor.getString(column_index)
            }
        } finally {
            if (cursor != null) cursor.close()
        }
        return null
    }

    fun isExternalStorageDocument(uri: Uri): Boolean {
        return "com.android.externalstorage.documents" == uri.authority
    }

    fun isDownloadsDocument(uri: Uri): Boolean {
        return "com.android.providers.downloads.documents" == uri.authority
    }

    fun isMediaDocument(uri: Uri): Boolean {
        return "com.android.providers.media.documents" == uri.authority
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data);
        val uri: Uri? = data?.data

        if (uri != null) {

           Log.d(TAG, "onSettingsaa: " + getPath(appContext, uri))
//            Log.d(TAG, "onActivityResult: " + mDB.path.dropLast(5))
            //Log.d(TAG, "onActivityResult: " + ((getPath(appContext,uri)?.substringAfterLast('/'))?.substringBefore('.')))

          mDBHelper.mergeDataBase(((getPath(appContext,uri)?.substringAfterLast('/'))?.substringBefore('.')), getPath(appContext,uri))
        }
    }
    @RequiresApi(Build.VERSION_CODES.R)
    fun onSettings(view: View) {
//        val SettingsIntent = Intent(this, SettingsActivity::class.java)
//        startActivity(SettingsIntent)
       // mDBHelper.mergeDataBase("test")

        Log.d(TAG, "onSettings: " + appContext.getApplicationInfo().dataDir + "/databases/qr.db")
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
            chooseFile.type = "*/*"
            chooseFile = Intent.createChooser(chooseFile, "Choose a file")
            startActivityForResult(chooseFile,code)
        }

    }


}
