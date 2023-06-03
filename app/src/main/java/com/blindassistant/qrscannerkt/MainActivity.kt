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
import android.provider.MediaStore.Audio.Media
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
import androidx.core.net.toUri
import com.blindassistant.qrscannerkt.databinding.ActivityMainBinding
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.*
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.sql.SQLException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream


private var Stopped: Boolean = false
private var Running: Boolean = false
private var AppId: String = "3257b0ae1f8d05fed50a757017a93688"
lateinit var mDB: SQLiteDatabase
private var mediaPlayer: MediaPlayer? = null
private var preInstalled = arrayOf("school10","bgitu")

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
                        isFound = true;
                        if(preInstalled.contains(libraryName)) {
                            mediaPlayer = MediaPlayer.create(MainActivity.appContext, appContext.resources.getIdentifier(libraryName + "_" + cursor.getString(1), "raw", appContext.packageName))
                            mediaPlayer?.setOnPreparedListener {
                                mediaPlayer?.start()
                            }
                            mediaPlayer?.setOnCompletionListener {
                                Stopped = false
                            }
                        } else {
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
            ).apply {
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                    add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }.toTypedArray()
    }

    fun getPath(context: Context, uri: Uri): String? {
        val isKitKatorAbove = Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT
        if (isKitKatorAbove && DocumentsContract.isDocumentUri(context, uri)) {
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
            var isFound: Boolean = false
            val cursor = mDB.rawQuery("SELECT * FROM info",null)
            cursor.moveToFirst()
            while (!cursor.isAfterLast()) {
                if(cursor.getString(0) == ((getPath(appContext,uri)?.substringAfterLast('/'))?.substringBefore('.'))) {
                    Toast.makeText(
                        MainActivity.appContext,
                        "Ошибка: Библиотека уже добавлена",
                        Toast.LENGTH_SHORT
                    ).show()
                    isFound = true
                    break
                }
                cursor.moveToNext()
            }
            cursor.close()

            if(!isFound && (((getPath(appContext,uri)?.substringAfterLast('/'))?.substringBefore('.'))) != "info") {
                // COPYING ZIP TO CACHE
                val libraryfile = getPath(appContext,uri)?.let { File(it) }
                val to = File(this.externalCacheDir.toString() + "/" + getPath(appContext,uri)?.substringAfterLast('/'))
                if(to.exists().not()) {
                    to.createNewFile()
                }
                if (libraryfile != null) {
                    libraryfile.copyTo(to, true)
                }

                try {
                    // TRYING TO UNZIP
                    unzipper(to)
                    //MERGING DATABASE
                    mDBHelper.mergeDataBase(((getPath(appContext,uri)?.substringAfterLast('/'))?.substringBefore('.')),
                        to.absolutePath.substringBeforeLast('.') + "/" + ((getPath(appContext,uri)?.substringAfterLast('/'))?.substringBefore('.')) + ".db")
                    //MOVING AUDIO FILES
                    File(this.externalCacheDir.toString() + "/" + (getPath(appContext,uri)?.substringAfterLast('/')
                        ?.substringBeforeLast('.')) + "/audio/").walk().forEach {
                        if(it.toString().substringAfterLast('.') == "mp3") {
                            Log.d(TAG, "onActivityResult: $it")
                            val destfile = File(this.getExternalFilesDir("audio").toString() + "/" + it.toString().substringAfterLast('/'))
                            if(destfile.exists().not()) {
                                destfile.createNewFile()
                            }
                            it.copyTo(destfile, true)
                        }
                    }
                    if(Running) { //restarting camera
                        onStart(viewBinding.root)
                        onStart(viewBinding.root)
                    }
                    //ERASING CACHE
                    val cacheDir: File? = this.externalCacheDir

                    val files = cacheDir?.listFiles()
                    if (files != null) {
                        for (file in files) file.delete()
                    }
                    val test = File(this.externalCacheDir.toString() + "/" + (getPath(appContext,uri)?.substringAfterLast('/')
                        ?.substringBeforeLast('.')) + "/")
                    test.deleteRecursively()

                    //Результат
                    Toast.makeText(
                        MainActivity.appContext,
                        "Библиотека добавлена",
                        Toast.LENGTH_SHORT
                    ).show()
                } catch (e: IOException) {
                    Toast.makeText(
                        MainActivity.appContext,
                        "Ошибка",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    @Throws(IOException::class)
    fun newFile(destinationDir: File, zipEntry: ZipEntry): File {
        val destFile = File(destinationDir, zipEntry.name)
        val destDirPath = destinationDir.canonicalPath
        val destFilePath = destFile.canonicalPath
        if (!destFilePath.startsWith(destDirPath + File.separator)) {
            throw IOException("Entry is outside of the target dir: " + zipEntry.name)
        }
        return destFile
    }

    fun unzipper(path: File) {
        val destDir = File(path.absolutePath.substringBeforeLast('.') + "/")
        val buffer = ByteArray(1024)
        val zis = ZipInputStream(FileInputStream(path.absolutePath))
        var zipEntry = zis.nextEntry
        while (zipEntry != null) {
            val newFile: File = newFile(destDir, zipEntry)
            if (zipEntry.isDirectory) {
                if (!newFile.isDirectory && !newFile.mkdirs()) {
                    throw IOException("Failed to create directory $newFile")
                }
            } else {
                val parent = newFile.parentFile
                if (!parent.isDirectory && !parent.mkdirs()) {
                    throw IOException("Failed to create directory $parent")
                }

                val fos = FileOutputStream(newFile)
                var len: Int
                while (zis.read(buffer).also { len = it } > 0) {
                    fos.write(buffer, 0, len)
                }
                fos.close()
            }
            zipEntry = zis.nextEntry
        }
        zis.closeEntry()
        zis.close()
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
