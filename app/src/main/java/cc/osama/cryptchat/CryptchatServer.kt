package cc.osama.cryptchat

import android.content.Context
import com.android.volley.Request
import com.android.volley.RequestQueue
import com.android.volley.Response
import com.android.volley.VolleyError
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import org.json.JSONObject

class CryptchatServer(private val context: Context, private val hostname: String) {
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

  fun get(
    path: String,
    param: JSONObject,
    success: (JSONObject) -> Unit =  {},
    failure: (error: VolleyError) -> Unit = {},
    always: () -> Unit = {}
  ) {
    request(Request.Method.GET, path, param, success, failure, always)
  }

  fun post(
    path: String,
    param: JSONObject,
    success: (JSONObject) -> Unit =  {},
    failure: (error: VolleyError) -> Unit = {},
    always: () -> Unit = {}
  ) {
    request(Request.Method.POST, path, param, success, failure, always)
  }

  fun put(
    path: String,
    param: JSONObject,
    success: (data: JSONObject) -> Unit = {},
    failure: (error: VolleyError) -> Unit = {},
    always: () -> Unit = {}
  ) {
    request(Request.Method.PUT, path, param, success, failure, always)
  }

  private fun request(
    method: Int,
    path: String,
    param: JSONObject,
    success: (data: JSONObject) -> Unit,
    failure: (error: VolleyError) -> Unit,
    always: () -> Unit = {}
  ) {
    val url = hostname + path
    val request = JsonObjectRequest(method, url, param, Response.Listener {
      success(it)
      always()
    }, Response.ErrorListener {
      failure(it)
      always()
    })
    QueueHandler.instance(context).queue.add(request)
  }
}