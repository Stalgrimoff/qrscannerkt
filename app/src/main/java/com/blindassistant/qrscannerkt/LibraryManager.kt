package com.blindassistant.qrscannerkt

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.math.BigInteger
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

class LibraryManager {
    fun newLibrary(uri: Uri?) {
        if (uri != null) {
            var isFound: Boolean = false
            val cursor = mDB.rawQuery("SELECT * FROM info",null)
            cursor.moveToFirst()
            while (!cursor.isAfterLast()) {
                if(cursor.getString(0) == ((getPath(MainActivity.appContext,uri)?.substringAfterLast('/'))?.substringBefore('.'))) {
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

            if(!isFound && (((getPath(MainActivity.appContext,uri)?.substringAfterLast('/'))?.substringBefore('.'))) != "info") {
                // COPYING ZIP TO CACHE
                val libraryfile = getPath(MainActivity.appContext,uri)?.let { File(it) }
                val to = File(MainActivity.appContext.externalCacheDir.toString() + "/" + getPath(MainActivity.appContext,uri)?.substringAfterLast('/'))
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
                    mDBHelper.mergeDataBase(((getPath(MainActivity.appContext,uri)?.substringAfterLast('/'))?.substringBefore('.')),
                        to.absolutePath.substringBeforeLast('.') + "/" + ((getPath(MainActivity.appContext,uri)?.substringAfterLast('/'))?.substringBefore('.')) + ".db", md5(((getPath(
                            MainActivity.appContext,uri)?.substringAfterLast('/'))?.substringBefore('.'))))
                    //MOVING AUDIO FILES
                    File(MainActivity.appContext.externalCacheDir.toString() + "/" + (getPath(MainActivity.appContext,uri)?.substringAfterLast('/')
                        ?.substringBeforeLast('.')) + "/audio/").walk().forEach {
                        if(it.toString().substringAfterLast('.') == "mp3") {
                            val destfile = File(MainActivity.appContext.getExternalFilesDir("audio").toString() + "/" + it.toString().substringAfterLast('/'))
                            if(destfile.exists().not()) {
                                destfile.createNewFile()
                            }
                            it.copyTo(destfile, true)
                        }
                    }

                    //ERASING CACHE
                    val cacheDir: File? = MainActivity.appContext.externalCacheDir

                    val files = cacheDir?.listFiles()
                    if (files != null) {
                        for (file in files) file.delete()
                    }
                    val test = File(MainActivity.appContext.externalCacheDir.toString() + "/" + (getPath(MainActivity.appContext,uri)?.substringAfterLast('/')
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
    fun newNetworkLibrary(path: String) {
        if((((path.substringAfterLast('/'))?.substringBefore('.'))) != "info") {
            try {
                // TRYING TO UNZIP
                unzipper(File(path))
                //MERGING DATABASE
                mDBHelper.mergeDataBase(((path.substringAfterLast('/'))?.substringBefore('.')),
                    path.substringBeforeLast('.') + "/" + ((path.substringAfterLast('/'))?.substringBefore('.')) + ".db", md5(((path.substringAfterLast('/'))?.substringBefore('.'))))
                //MOVING AUDIO FILES
                File(MainActivity.appContext.externalCacheDir.toString() + "/" + (path.substringAfterLast('/')
                    ?.substringBeforeLast('.')) + "/audio/").walk().forEach {
                    if(it.toString().substringAfterLast('.') == "mp3") {
                        val destfile = File(MainActivity.appContext.getExternalFilesDir("audio").toString() + "/" + it.toString().substringAfterLast('/'))
                        if(destfile.exists().not()) {
                            destfile.createNewFile()
                        }
                        it.copyTo(destfile, true)
                    }
                }

                //ERASING CACHE
                val cacheDir: File? = MainActivity.appContext.externalCacheDir

                val files = cacheDir?.listFiles()
                if (files != null) {
                    for (file in files) file.delete()
                }
                val test = File(MainActivity.appContext.externalCacheDir.toString() + "/" + (path.substringAfterLast('/')
                    ?.substringBeforeLast('.')) + "/")
                test.deleteRecursively()

                //Результат
                Toast.makeText(
                    MainActivity.appContext,
                    "Библиотека добавлена",
                    Toast.LENGTH_SHORT
                ).show()
                blockedArray.remove(md5(((path.substringAfterLast('/'))?.substringBefore('.'))))
            } catch (e: IOException) {
                Toast.makeText(
                    MainActivity.appContext,
                    "Ошибка",
                    Toast.LENGTH_SHORT
                ).show()
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

    fun md5(input: String?): String {
        val md = MessageDigest.getInstance("MD5")
        if (input != null) {
            return BigInteger(1, md.digest(input.toByteArray())).toString(16).padStart(32, '0')
        }
        return ""
    }

}