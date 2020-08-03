package cc.osama.cryptchat

import android.os.Handler
import android.os.Looper
import android.util.Log.w
import cc.osama.cryptchat.db.Server
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
import java.util.concurrent.Executors
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

class CryptchatRequest(
  val url: String,
  val method: Methods,
  val async: Boolean = true,
  private val requestHeaders: HashMap<String, String>? = null
) {
  enum class Methods { GET, PUT, POST, DELETE }
  class TooLateCallbacksDeclarationException : Exception()

  class OnUiThread {
    fun onUiThread(callback: OnUiThread.() -> Unit) {
      if (Looper.myLooper() == Looper.getMainLooper()) {
        callback()
      } else {
        handler.post {
          callback()
        }
      }
    }
  }

  companion object {
    private val executor = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())
    private fun queue(request: CryptchatRequest) {
      executor.execute {
        request.execute()
      }
    }
  }

  class ErrorMetadata(
    val statusCode: Int,
    val isClientError: Boolean = false,
    val isServerError: Boolean = false,
    val isUnknownHostError: Boolean = false,
    val isNoConnectionError: Boolean = false,
    val isTimeoutError: Boolean = false,
    val hadEncodingError: Boolean = false,
    val hadMalformedJson: Boolean = false,
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
        "hadEncodingError: $hadEncodingError",
        "hadMalformedJson: $hadMalformedJson",
        "serverMessages: ${serverMessages.joinToString(", ")}",
        "fullError: $fullError"
      ).joinToString("\n")
    }
  }

  private var responseHeaders: Map<String, String>? = null
  private var statusCode = -1

  private var initiated = false
  private var body: ByteArray? = null

  private var successCallback: (OnUiThread.(JSONObject) -> Unit)? = null
  private var failureCallback: (OnUiThread.(ErrorMetadata) -> Unit)? = null
  private var alwaysCallback: (OnUiThread.(Boolean) -> Unit)? = null
  private var headersCallback: (OnUiThread.(Map<String, String>) -> Unit)? = null

  private val uiThreadProvider = OnUiThread()

  fun perform(body: ByteArray) {
    this.body = body
    perform()
  }

  fun perform(params: JSONObject) {
    this.body = params.toString().toByteArray(Charsets.UTF_8)
    perform()
  }

  fun perform() {
    if (async) {
      queue(this)
    } else {
      execute()
    }
  }

  fun success(callback: OnUiThread.(JSONObject) -> Unit) {
    setCallback {
      successCallback = callback
    }
  }

  fun failure(callback: OnUiThread.(ErrorMetadata) -> Unit) {
    setCallback {
      failureCallback = callback
    }
  }

  fun always(callback: OnUiThread.(Boolean) -> Unit) {
    setCallback {
      alwaysCallback = callback
    }
  }

  fun headers(callback: OnUiThread.(Map<String, String>) -> Unit) {
    setCallback {
      headersCallback = callback
    }
  }

  private fun success(json: JSONObject) {
    successCallback?.invoke(uiThreadProvider, json)
    alwaysCallback?.invoke(uiThreadProvider, true)
  }

  private fun failure(errorData: ErrorMetadata) {
    failureCallback?.invoke(uiThreadProvider, errorData)
    alwaysCallback?.invoke(uiThreadProvider, false)
  }

  private fun headers() {
    val headers = responseHeaders
    if (headers != null) {
      headersCallback?.invoke(uiThreadProvider, headers)
    }
  }

  private fun setCallback(lambda: () -> Unit) {
    if (initiated) {
      throw TooLateCallbacksDeclarationException()
    } else {
      lambda()
    }
  }

  private fun execute() {
    var isEncodingError = false
    var isMalformedJsonError = false
    try {
      val response = connect()
      isEncodingError = response == null
      val json = if (response != null) {
        try {
          processResponse(response)
        } catch (ex: JSONException) {
          isMalformedJsonError = true
          JSONObject()
        }
      } else {
        JSONObject()
      }
      val errorMessages = ArrayList<String>().also { list ->
        if (statusCode >= 400) {
          // TODO: use opt{type} methods everywhere we use JSON
          (json.optJSONArray("messages"))?.also {
            for (m in 0 until it.length()) {
              (it.get(m) as? String)?.also { msg -> list.add(msg) }
            }
          }
        }
      }.toTypedArray()
      when (statusCode) {
        -1 -> {
          // I doubt this case is needed, but let's add just to
          // be on the safe side
          w("CRYPTCHAT HTTP ERROR", "WEIRD CONDITION OCCURRED!")
          failure(ErrorMetadata(
            statusCode = statusCode,
            hadMalformedJson = isMalformedJsonError,
            hadEncodingError = isEncodingError,
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
            hadMalformedJson = isMalformedJsonError,
            hadEncodingError = isEncodingError,
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
    } catch (ex: IOException) {
      failure(ErrorMetadata(
        statusCode = statusCode,
        isNoConnectionError = true,
        originalError = ex
      ))
    // } catch (ex: Throwable) {
    //   failure(ErrorMetadata(
    //     statusCode = statusCode,
    //     hadMalformedJson = isMalformedJsonError,
    //     hadEncodingError = isEncodingError,
    //     originalError = ex
    //   ))
    } finally {
      headers()
    }
}

  private fun connect() : String? {
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
    val body = body
    // GET requests should not have body
    if (method != Methods.GET && body != null && body.isNotEmpty()) {
      connection.doOutput = true
      connection.outputStream.write(body, 0, body.size)
    }
    statusCode = connection.responseCode
    responseHeaders = HashMap<String, String>().let {
      for (i in connection.headerFields ?: HashMap<String, List<String>>()) {
        if (i.key == null) continue
        // this is probably not perfect for all headers
        // but should be good enough for us
        it[i.key.toLowerCase(Locale.ROOT)] = i.value?.joinToString(", ") ?: ""
      }
      it.toMap()
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
    } catch (ex: UnsupportedEncodingException) {
      return null
    } finally {
      stream?.close()
    }
  }

  private fun processResponse(response: String?) : JSONObject {
    return JSONObject(response)
  }
}