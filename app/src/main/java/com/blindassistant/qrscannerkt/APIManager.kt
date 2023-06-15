package com.blindassistant.qrscannerkt

import android.util.Log
import android.widget.Toast
import androidx.camera.core.ExperimentalGetImage
import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.math.BigInteger
import java.security.MessageDigest


class APIManager {
    data class UserInfo (
        @SerializedName("info") val name: String?
    )
    interface RestApi {

        @Headers("Content-Type: application/json")
        @POST("api")
        fun requestInfo(@Body userData: UserInfo): Call<UserInfo>

        @GET("api/sendfile")
        fun downloadFileWithFixedUrl(@Header("info") name:String): Call<ResponseBody?>?
    }
    object ServiceBuilder {
        private val client = OkHttpClient.Builder().build()

        private val retrofit = Retrofit.Builder()
            .baseUrl("http://192.168.17.122:8081")
            .addConverterFactory(GsonConverterFactory.create())
            .client(client)
            .build()

        fun<T> buildService(service: Class<T>): T{
            return retrofit.create(service)
        }
    }

    @ExperimentalGetImage class RestApiService {
        fun requestInfo(userData: UserInfo, onResult: (UserInfo?) -> Unit){
            val retrofit = ServiceBuilder.buildService(RestApi::class.java)
            retrofit.requestInfo(userData).enqueue(
                object : Callback<UserInfo> {
                    override fun onFailure(call: Call<UserInfo>, t: Throwable) {
                        onResult(null)
                    }
                    override fun onResponse(call: Call<UserInfo>, response: Response<UserInfo>) {
                        val addedUser = response.body()
                        onResult(addedUser)
                    }
                }
            )
        }

        fun md5(input: String?): String {
            val md = MessageDigest.getInstance("MD5")
            if (input != null) {
                return BigInteger(1, md.digest(input.toByteArray())).toString(16).padStart(32, '0')
            }
            return ""
        }

        fun downloadFile(libraryName: String) {
            val apiInterface = ServiceBuilder.buildService(RestApi::class.java)
            val call: Call<ResponseBody?>? = apiInterface.downloadFileWithFixedUrl(libraryName)
            if (call != null) {
                call.enqueue(object : Callback<ResponseBody?> {
                    override fun onResponse(
                        call: Call<ResponseBody?>,
                        response: Response<ResponseBody?>
                    ) {
                        if (response.isSuccessful) {
                            val writtenToDisk: Boolean = response.body()?.let { writeResponseBodyToDisk(it,libraryName) } == true
                            Log.d("File download was a success? ", writtenToDisk.toString())
                            if (writtenToDisk) {
                                LibraryManager().newNetworkLibrary(MainActivity.appContext.externalCacheDir.toString() + "/" + libraryName + ".zip")
                            } else {
                                blockedArray.remove(md5(libraryName))
                            }
                        }
                    }

                    override fun onFailure(call: Call<ResponseBody?>, t: Throwable) {
                        Toast.makeText(
                            MainActivity.appContext,
                            "NO INTERNET",
                            Toast.LENGTH_SHORT
                        ).show()
                        blockedArray.remove(md5(libraryName))
                    }

                })
            }
        }


        private fun writeResponseBodyToDisk(body: ResponseBody, libraryName: String): Boolean {
            return try {
                val futureStudioIconFile: File =
                    File(MainActivity.appContext.externalCacheDir.toString() + "/" + libraryName + ".zip")
                var inputStream: InputStream? = null
                var outputStream: OutputStream? = null
                try {
                    val fileReader = ByteArray(4096)
                    val fileSize = body.contentLength()
                    var fileSizeDownloaded: Long = 0
                    inputStream = body.byteStream()
                    outputStream = FileOutputStream(futureStudioIconFile)
                    while (true) {
                        val read = inputStream.read(fileReader)
                        if (read == -1) {
                            break
                        }
                        outputStream.write(fileReader, 0, read)
                        fileSizeDownloaded += read.toLong()
                        Log.d("File Download: ", "$fileSizeDownloaded of $fileSize")
                    }
                    outputStream.flush()
                    true
                } catch (e: IOException) {
                    false
                } finally {
                    inputStream?.close()
                    outputStream?.close()
                }
            } catch (e: IOException) {
                false
            }
        }
    }
}