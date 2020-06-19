package cc.osama.cryptchat

import android.content.Context
import android.util.Log.e
import android.util.Log.w
import cc.osama.cryptchat.db.Server
import com.android.volley.*
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.UnsupportedEncodingException
import java.net.UnknownHostException
import java.nio.charset.Charset

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

  class CryptchatServerError(
    val statusCode: Int?,
    val serverMessages: ArrayList<String>,
    val volleyError: VolleyError
  ) {
    override fun toString() : String {
      return "Status code: ${this.statusCode ?: "Unknown"}\n" +
        "Server messages: $serverMessages\n" +
        "Original Volley error: $volleyError"
    }
  }

  companion object {
    const val AUTH_TOKEN_HEADER = "Cryptchat-Auth-Token"
    fun checkAddress(
      context: Context,
      address: String,
      success: (data: JSONObject) -> Unit,
      failure: (error: VolleyError) -> Unit
    ) {
      val request = JsonObjectRequest(
        Request.Method.GET,
        "$address/knock-knock.json",
        JSONObject(),
        Response.Listener<JSONObject> {
          success(it)
        },
        Response.ErrorListener {
          failure(it)
        }
      )
      QueueHandler.instance(context).queue.add(request)
    }

    fun registerAtServer(
      context: Context,
      address: String,
      params: JSONObject,
      success: (data: JSONObject) -> Unit,
      failure: (error: VolleyError) -> Unit
    ) {
      val request = JsonObjectRequest(
        Request.Method.POST,
        "$address/register.json",
        params,
        Response.Listener<JSONObject> {
          success(it)
        },
        Response.ErrorListener {
          failure(it)
        }
      )
      QueueHandler.instance(context).queue.add(request)
    }
  }

  private class CryptchatJsonRequest(
    method: Int,
    url: String,
    jsonRequest: JSONObject?,
    listener: Response.Listener<JSONObject>,
    errorListener : Response.ErrorListener,
    val headersHandler: (Map<String, String>?) -> Unit,
    val extraHeaders: HashMap<String, String>
  ) : JsonObjectRequest(method, url, jsonRequest, listener, errorListener) {
    override fun parseNetworkResponse(response: NetworkResponse?): Response<JSONObject> {
      headersHandler(response?.headers)
      return super.parseNetworkResponse(response)
    }

    override fun getHeaders() : MutableMap<String, String> {
      val superHeaders = super.getHeaders()
      val newHeaders = HashMap(superHeaders)
      for (it in extraHeaders) {
        newHeaders[it.key] = it.value
      }
      return newHeaders
    }
  }

  fun get(
    path: String,
    param: JSONObject? = null,
    success: (JSONObject) -> Unit =  {},
    failure: (CryptchatServerError) -> Unit = {},
    always: () -> Unit = {},
    authenticate: Boolean
  ) {
    request(Request.Method.GET, path, param, success, failure, always, authenticate = authenticate)
  }

  fun post(
    path: String,
    param: JSONObject? = null,
    success: (JSONObject) -> Unit =  {},
    failure: (CryptchatServerError) -> Unit = {},
    always: () -> Unit = {},
    authenticate: Boolean = true
  ) {
    request(Request.Method.POST, path, param, success, failure, always, authenticate = authenticate)
  }

  fun put(
    path: String,
    param: JSONObject? = null,
    success: (data: JSONObject) -> Unit = {},
    failure: (CryptchatServerError) -> Unit = {},
    always: () -> Unit = {},
    authenticate: Boolean = true
  ) {
    request(Request.Method.PUT, path, param, success, failure, always, authenticate = authenticate)
  }

  private fun request(
    method: Int,
    path: String,
    param: JSONObject? = null,
    success: (data: JSONObject) -> Unit,
    failure: (CryptchatServerError) -> Unit = {},
    always: () -> Unit = {},
    headersCallback: (Map<String, String>?) -> Unit = {},
    extraHeaders: HashMap<String, String> = HashMap(),
    authenticate: Boolean = true
  ) {
    val url = server.address + path
    val headers = HashMap(extraHeaders)
    if (authenticate) {
      headers[AUTH_TOKEN_HEADER] = server.authToken
    }
    val request = CryptchatJsonRequest(
      method,
      url,
      param,
      Response.Listener {
        success(it)
        always()
      },
      Response.ErrorListener {
        val errorMessages = ArrayList<String>()
        if (it?.networkResponse?.data != null) {
          val errorJson = try {
            val jsonString = String(it.networkResponse.data, Charset.forName("UTF-8"))
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
        val statusCode = it?.networkResponse?.statusCode
        failure(CryptchatServerError(
          statusCode = statusCode,
          serverMessages = errorMessages,
          volleyError = it
        ))
        always()
      },
      headersHandler = {
        if (it == null) return@CryptchatJsonRequest
        val authToken = it[AUTH_TOKEN_HEADER]
        if (authenticate && authToken != null && authToken.isNotEmpty()) {
          server.authToken = authToken
          AsyncExec.run {
            Cryptchat.db(context).servers().update(server)
          }
        }
        headersCallback(it)
      },
      extraHeaders = headers
    )
    QueueHandler.instance(context).queue.add(request)
  }
}