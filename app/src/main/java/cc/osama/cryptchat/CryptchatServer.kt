package cc.osama.cryptchat

import android.content.Context
import android.util.Base64
import android.util.Log.e
import android.util.Log.w
import cc.osama.cryptchat.db.Server
import com.android.volley.*
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.JsonRequest
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.UnsupportedEncodingException
import java.lang.StringBuilder
import java.net.UnknownHostException
import java.nio.charset.Charset
import java.security.SecureRandom
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

class CryptchatServer(private val context: Context, private val server: Server) {
  private class QueueHandler(val context: Context) {
    companion object {
      private var INSTANCE: QueueHandler? = null
      fun instance(context: Context) =
        INSTANCE ?: synchronized(this) {
          INSTANCE ?: QueueHandler(context).also {
            INSTANCE = it
          }
        }
    }

    val queue: RequestQueue by lazy {
      Volley.newRequestQueue(context)
    }
  }

  private open class CryptchatJsonRequest(
    method: Int,
    url: String,
    jsonRequest: JSONObject?,
    listener: Response.Listener<JSONObject>,
    errorListener : Response.ErrorListener,
    val extraHeaders: HashMap<String, String>?,
    val headersHandler: ((Map<String, String>?) -> Unit)? = null
  ) : JsonObjectRequest(method, url, jsonRequest, listener, errorListener) {
    override fun parseNetworkResponse(response: NetworkResponse?): Response<JSONObject> {
      headersHandler?.invoke(response?.headers)
      return super.parseNetworkResponse(response)
    }

    override fun getHeaders() : MutableMap<String, String> {
      val superHeaders = super.getHeaders()
      if (extraHeaders == null) {
        return superHeaders
      }
      val newHeaders = HashMap(superHeaders)
      for (it in extraHeaders) {
        newHeaders[it.key] = it.value
      }
      return newHeaders
    }
  }

  private class CryptchatMultipartFormDataRequest(
    url: String,
    listener: Response.Listener<JSONObject>,
    errorListener: Response.ErrorListener,
    extraHeaders: HashMap<String, String>?,
    headersHandler: ((Map<String, String>?) -> Unit)?,
    private val form: JSONObject? = null,
    private val file: ByteArray,
    private val fileContentType: String
  ) : CryptchatJsonRequest(
    Method.POST,
    url,
    form,
    listener,
    errorListener,
    extraHeaders,
    headersHandler
  ) {
    private val boundary = "--CryptchatFormBoundary" + CryptchatUtils.secureRandomHex(8)
    private val body = ByteArrayOutputStream().apply {
      val contentDisposition = "Content-Disposition"
      val contentType = "Content-Type"
      form?.keys()?.forEach { key ->
        write("--$boundary".toByteArray())
        write("\r\n$contentDisposition: form-data; name=\"$key\"".toByteArray())
        write("\r\n\r\n${form[key]}\r\n".toByteArray())
      }
      write("--$boundary".toByteArray())
      write("\r\n$contentDisposition: form-data; name=\"file\"; filename=\"file.${
        fileContentType.split("/").last()
      }\"".toByteArray())
      write("\r\n$contentType: $fileContentType".toByteArray())
      write("\r\n\r\n".toByteArray())
      write(file)
      write("\r\n".toByteArray())
      write("--$boundary--".toByteArray())
    }.toByteArray()

    override fun getBody(): ByteArray {
      return body
    }

    override fun getBodyContentType(): String {
      return "multipart/form-data; boundary=$boundary"
    }
  }

  class CryptchatServerError(
    val statusCode: Int?,
    val serverMessages: ArrayList<String>,
    val volleyError: VolleyError?
  ) {
    override fun toString() : String {
      return "Status code: ${this.statusCode ?: "Unknown"}\n" +
        "Server messages: $serverMessages\n" +
        "Original Volley error: $volleyError"
    }
  }

  companion object {
    const val AUTH_TOKEN_HEADER = "Cryptchat-Auth-Token"

    private fun defaultErrorHandler(
      volleyError: VolleyError?,
      failure: ((CryptchatServerError) -> Unit)? = null,
      always: (() -> Unit)? = null
    ) {
      if (failure == null && always == null) return
      val errorMessages = ArrayList<String>()
      val data = volleyError?.networkResponse?.data
      if (data != null) {
        val errorJson = try {
          val jsonString = String(data, Charset.forName("UTF-8"))
          JSONObject(jsonString)
        } catch (ex: UnsupportedEncodingException) {
          null
        } catch (ex: JSONException) {
          null
        }
        if (errorJson != null) {
          val messages = errorJson["messages"] as? JSONArray
          for (i in 0 until (messages?.length() ?: 0)) {
            (messages?.get(i) as? String)?.also { msg -> errorMessages.add(msg) }
          }
        }
      }
      val statusCode = volleyError?.networkResponse?.statusCode
      failure?.invoke(CryptchatServerError(
        statusCode = statusCode,
        serverMessages = errorMessages,
        volleyError = volleyError
      ))
      always?.invoke()
    }

    fun checkAddress(
      context: Context,
      address: String,
      success: (data: JSONObject) -> Unit,
      failure: (CryptchatServerError) -> Unit
    ) {
      val request = JsonObjectRequest(
        Request.Method.GET,
        "$address/knock-knock.json",
        JSONObject(),
        Response.Listener<JSONObject> {
          success(it)
        },
        Response.ErrorListener {
          defaultErrorHandler(
            volleyError = it,
            failure = failure
          )
        }
      )
      QueueHandler.instance(context).queue.add(request)
    }

    fun registerAtServer(
      context: Context,
      address: String,
      params: JSONObject,
      success: (data: JSONObject) -> Unit,
      failure: (CryptchatServerError) -> Unit
    ) {
      val request = JsonObjectRequest(
        Request.Method.POST,
        "$address/register.json",
        params,
        Response.Listener<JSONObject> {
          success(it)
        },
        Response.ErrorListener {
          defaultErrorHandler(
            volleyError = it,
            failure = failure
          )
        }
      )
      QueueHandler.instance(context).queue.add(request)
    }
  }

  fun get(
    path: String,
    param: JSONObject? = null,
    success: ((JSONObject) -> Unit)? = null,
    failure: ((CryptchatServerError) -> Unit)? = null,
    always: (() -> Unit)? = null,
    authenticate: Boolean
  ) {
    request(Request.Method.GET, path, param, success, failure, always, authenticate = authenticate)
  }

  fun post(
    path: String,
    param: JSONObject? = null,
    success: ((JSONObject) -> Unit)? = null,
    failure: ((CryptchatServerError) -> Unit)? = null,
    always: (() -> Unit)? = null,
    authenticate: Boolean = true
  ) {
    request(Request.Method.POST, path, param, success, failure, always, authenticate = authenticate)
  }

  fun upload(
    path: String,
    form: JSONObject? = null,
    file: ByteArray,
    fileContentType: String,
    success: ((JSONObject) -> Unit)? = null,
    failure: ((CryptchatServerError) -> Unit)? = null,
    always: (() -> Unit)? = null,
    authenticate: Boolean = true
  ) {
    val extraHeaders = if (authenticate) HashMap<String, String>().also {
      it[AUTH_TOKEN_HEADER] = server.authToken
    } else null
    val request = CryptchatMultipartFormDataRequest(
      url = "${server.address}$path",
      form = form,
      file = file,
      fileContentType = fileContentType,
      listener = Response.Listener {
        success?.invoke(it)
        always?.invoke()
      },
      errorListener = Response.ErrorListener {
        defaultErrorHandler(it, failure, always)
      },
      extraHeaders = extraHeaders,
      headersHandler = {
        defaultHeadersHandler(
          headers = it,
          authenticate = authenticate
        )
      }
    )
    QueueHandler.instance(context).queue.add(request)
  }

  fun put(
    path: String,
    param: JSONObject? = null,
    success: ((JSONObject) -> Unit)? = null,
    failure: ((CryptchatServerError) -> Unit)? = null,
    always: (() -> Unit)? = null,
    authenticate: Boolean = true
  ) {
    request(Request.Method.PUT, path, param, success, failure, always, authenticate = authenticate)
  }

  private fun request(
    method: Int,
    path: String,
    param: JSONObject? = null,
    success: ((JSONObject) -> Unit)? = null,
    failure: ((CryptchatServerError) -> Unit)? = null,
    always: (() -> Unit)? = null,
    authenticate: Boolean = true
  ) {
    val url = server.address + path
    val headers = if (authenticate) HashMap<String, String>().also {
      it[AUTH_TOKEN_HEADER] = server.authToken
    } else null
    val request = CryptchatJsonRequest(
      method,
      url,
      param,
      Response.Listener {
        success?.invoke(it)
        always?.invoke()
      },
      Response.ErrorListener {
        defaultErrorHandler(
          volleyError = it,
          failure = failure,
          always = always
        )
      },
      headersHandler = {
        defaultHeadersHandler(
          headers = it,
          authenticate = authenticate
        )
      },
      extraHeaders = headers
    )
    QueueHandler.instance(context).queue.add(request)
  }

  private fun defaultHeadersHandler(
    headers: Map<String, String>?,
    authenticate: Boolean
  ) {
    if (headers == null) return
    val authToken = headers[AUTH_TOKEN_HEADER]
    if (authenticate && authToken != null && authToken.isNotEmpty()) {
      server.authToken = authToken
      AsyncExec.run {
        Cryptchat.db(context).servers().update(server)
      }
    }
  }

}