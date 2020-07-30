package cc.osama.cryptchat

import android.util.Log.w
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.*
import java.lang.Exception
import java.lang.StringBuilder
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

class CryptchatRequest(
  val url: String,
  val method: Methods,
  val body: ByteArray?,
  val successCallback: (JSONObject) -> Unit,
  val failureCallback: (ErrorBox) -> Unit,
  private val requestHeaders: HashMap<String, String>? = null
) {
  enum class Methods { GET, PUT, POST, DELETE }
  class ErrorBox(
    val statusCode: Int,
    val isClientError: Boolean = false,
    val isServerError: Boolean = false,
    val isUnknownHostError: Boolean = false,
    val isNoConnectionError: Boolean = false,
    val isTimeoutError: Boolean = false,
    val isEncodingError: Boolean = false,
    val serverMessages: Array<String> = emptyArray(),
    val originalError: Throwable? = null
  )

  private class EmptyResponseError : Exception()
  private var headers: HashMap<String, String>? = null
  private var statusCode = -1
  private var rawResponse: String? = null

  fun run() {
    perform()
  }

  fun headers() : HashMap<String, String>? = headers

  private fun perform() {
    try {
      performSync()
      val json = processResponse(rawResponse)
      val errorMessages = ArrayList<String>().also { list ->
        if (statusCode >= 400) {
          (json["messages"] as? JSONArray)?.also {
            for (m in 0 until it.length()) {
              (it.get(m) as? String)?.also { msg -> list.add(msg) }
            }
          }
        }
      }.toTypedArray()
      when (statusCode) {
        -1 -> {
          w("CRYPTCHAT HTTP ERROR", "WEIRD CONDITION OCCURRED!")
          failureCallback(ErrorBox(
            statusCode = statusCode,
            serverMessages = errorMessages
          ))
        }
        in 200..299 -> {
          successCallback(json)
        }
        else -> {
          failureCallback(ErrorBox(
            statusCode = statusCode,
            serverMessages = errorMessages,
            isClientError = statusCode in 400..499,
            isServerError = statusCode !in 400..499
          ))
        }
      }
    } catch (ex: UnknownHostException) {
      failureCallback(ErrorBox(
        statusCode = statusCode,
        isUnknownHostError = true,
        originalError = ex
      ))
    } catch (ex: SocketTimeoutException) {
      failureCallback(ErrorBox(
        statusCode = statusCode,
        isTimeoutError = true,
        originalError = ex
      ))
    } catch (ex: UnsupportedEncodingException) {
      failureCallback(ErrorBox(
        statusCode = statusCode,
        isEncodingError = true,
        originalError = ex
      ))
    } catch (ex: IOException) {
      failureCallback(ErrorBox(
        statusCode = statusCode,
        isNoConnectionError = true,
        originalError = ex
      ))
    } catch (ex: Throwable) {
      failureCallback(ErrorBox(
        statusCode = statusCode,
        originalError = ex
      ))
    }
}

  private fun performSync() {
    val urlObj = URL(url)
    val connection = urlObj.openConnection() as HttpURLConnection
    connection.requestMethod = method.name
    connection.setRequestProperty("Content-Type", "application/json; utf-8")
    connection.setRequestProperty("Accept", "application/json")
    connection.connectTimeout = 10000
    if (requestHeaders != null) {
      for (header in requestHeaders) {
        connection.setRequestProperty(header.key, header.value)
      }
    }
    // GET requests should not have body
    if (method != Methods.GET && body != null && body.isNotEmpty()) {
      connection.doOutput = true
      connection.outputStream.write(body, 0, body.size)
    }
    statusCode = connection.responseCode
    headers = HashMap<String, String>().also {
      for (i in connection.headerFields ?: HashMap<String, List<String>>()) {
        if (i.key == null) continue
        // this is probably not perfect for all headers
        // but should be good enough for us
        it[i.key.toLowerCase(Locale.ROOT)] = i.value?.joinToString(", ") ?: ""
      }
    }
    var stream: InputStream? = null
    try {
      stream = try {
        connection.inputStream
      } catch (ex: IOException) {
        connection.errorStream
      }
      // BufferedReader and InputStreamReader speed things up
      // according to the documentation
      val input = BufferedReader(InputStreamReader(stream, Charsets.UTF_8))
      val responseBuilder = StringBuilder()
      var line = input.readLine()
      while (line != null) {
        responseBuilder.append(line)
        line = input.readLine()
      }
      rawResponse = responseBuilder.toString()
    } finally {
      stream?.close()
    }
  }

  private fun processResponse(response: String?) : JSONObject {
    return JSONObject(response)
  }
}