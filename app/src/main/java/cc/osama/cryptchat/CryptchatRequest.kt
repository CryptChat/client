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
  private val requestHeaders: HashMap<String, String>? = null
) {
  enum class Methods { GET, PUT, POST, DELETE }
  class TooLateCallbacksDeclarationException : Exception()

  class ErrorMetadata(
    val statusCode: Int,
    val isClientError: Boolean = false,
    val isServerError: Boolean = false,
    val isUnknownHostError: Boolean = false,
    val isNoConnectionError: Boolean = false,
    val isTimeoutError: Boolean = false,
    val isEncodingError: Boolean = false,
    val isMalformedJsonError: Boolean = false,
    val serverMessages: Array<String> = emptyArray(),
    val originalError: Throwable? = null
  ) {
    override fun toString() : String {
      val fullError = if (originalError != null) {
        arrayOf(
          "${originalError.javaClass}",
          "${originalError.message}",
          originalError.stackTrace.joinToString("\n")
        ).joinToString("\n")
      } else {
        "null"
      }
      return arrayOf(
        "statusCode: $statusCode",
        "isClientError: $isClientError",
        "isServerError: $isServerError",
        "isUnknownHostError: $isUnknownHostError",
        "isNoConnectionError: $isNoConnectionError",
        "isTimeoutError: $isTimeoutError",
        "isEncodingError: $isEncodingError",
        "isMalformedJsonError: $isMalformedJsonError",
        "serverMessages: ${serverMessages.joinToString(", ")}",
        "fullError: $fullError"
      ).joinToString("\n")
    }
  }

  private var headers: HashMap<String, String>? = null
  private var statusCode = -1

  private var initiated = false
  private var successCallback: ((JSONObject) -> Unit)? = null
  private var failureCallback: ((ErrorMetadata) -> Unit)? = null
  private var alwaysCallback: ((Boolean) -> Unit)? = null

  fun headers() : HashMap<String, String>? = headers

  fun perform() {
    execute()
  }

  fun success(callback: (JSONObject) -> Unit) {
    setCallback {
      successCallback = callback
    }
  }

  fun failure(callback: (ErrorMetadata) -> Unit) {
    setCallback {
      failureCallback = callback
    }
  }

  fun always(callback: (Boolean) -> Unit) {
    setCallback {
      alwaysCallback = callback
    }
  }

  private fun success(json: JSONObject) {
    successCallback?.invoke(json)
    alwaysCallback?.invoke(true)
  }

  private fun failure(errorData: ErrorMetadata) {
    failureCallback?.invoke(errorData)
    alwaysCallback?.invoke(false)
  }

  private fun setCallback(lambda: () -> Unit) {
    if (initiated) {
      throw TooLateCallbacksDeclarationException()
    } else {
      lambda()
    }
  }

  private fun execute() {
    try {
      val response = connect()
      val json = processResponse(response)
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
          failure(ErrorMetadata(
            statusCode = statusCode,
            serverMessages = errorMessages
          ))
        }
        in 200..299 -> {
          success(json)
        }
        else -> {
          failure(ErrorMetadata(
            statusCode = statusCode,
            serverMessages = errorMessages,
            isClientError = statusCode in 400..499,
            isServerError = statusCode !in 400..499
          ))
        }
      }
    } catch (ex: UnknownHostException) {
      failure(ErrorMetadata(
        statusCode = statusCode,
        isUnknownHostError = true,
        originalError = ex
      ))
    } catch (ex: SocketTimeoutException) {
      failure(ErrorMetadata(
        statusCode = statusCode,
        isTimeoutError = true,
        originalError = ex
      ))
    } catch (ex: UnsupportedEncodingException) {
      failure(ErrorMetadata(
        statusCode = statusCode,
        isEncodingError = true,
        originalError = ex
      ))
    } catch (ex: JSONException) {
      failure(ErrorMetadata(
        statusCode = statusCode,
        isMalformedJsonError = true,
        originalError = ex
      ))
    } catch (ex: IOException) {
      failure(ErrorMetadata(
        statusCode = statusCode,
        isNoConnectionError = true,
        originalError = ex
      ))
    } catch (ex: Throwable) {
      failure(ErrorMetadata(
        statusCode = statusCode,
        originalError = ex
      ))
    }
}

  private fun connect() : String {
    initiated = true
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
      return responseBuilder.toString()
    } finally {
      stream?.close()
    }
  }

  private fun processResponse(response: String?) : JSONObject {
    return JSONObject(response)
  }
}