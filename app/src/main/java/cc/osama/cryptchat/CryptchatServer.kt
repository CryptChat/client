package cc.osama.cryptchat

import android.content.Context
import android.util.Log.d
import cc.osama.cryptchat.db.Server
import cc.osama.cryptchat.worker.SupplyEphemeralKeysWorker
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.lang.NumberFormatException
import java.lang.StringBuilder
import kotlin.collections.HashMap

class CryptchatServer(private val context: Context, private val server: Server) {
  companion object {
    private const val AUTH_TOKEN_HEADER = "Cryptchat-Auth-Token"
    private const val AUTH_USER_ID_HEADER = "Cryptchat-Auth-User-Id"
    private const val REMAINING_KEYS_COUNT = "Cryptchat-Remaining-Keys-Count"
    private const val IS_ADMIN_HEADER = "Cryptchat-Admin"

    fun checkAddress(
      address: String,
      success: CryptchatRequest.OnUiThread.(JSONObject) -> Unit,
      failure: CryptchatRequest.OnUiThread.(CryptchatRequest.ErrorMetadata) -> Unit
    ) {
      CryptchatRequest(
        url = "$address/knock-knock.json",
        method = CryptchatRequest.Methods.GET
      ).apply {
        success(success)
        failure(failure)
        perform()
      }
    }

    fun registerAtServer(
      address: String,
      params: JSONObject,
      success: CryptchatRequest.OnUiThread.(JSONObject) -> Unit,
      failure: CryptchatRequest.OnUiThread.(CryptchatRequest.ErrorMetadata) -> Unit,
      async: Boolean
    ) {
      CryptchatRequest(
        async = async,
        url = "$address/register.json",
        method = CryptchatRequest.Methods.POST
      ).apply {
        success(success)
        failure(failure)
        perform(params)
      }
    }
  }

  fun upload(
    path: String,
    form: JSONObject? = null,
    file: ByteArray,
    fileContentType: String,
    success: (CryptchatRequest.OnUiThread.(JSONObject) -> Unit)? = null,
    failure: (CryptchatRequest.OnUiThread.(CryptchatRequest.ErrorMetadata) -> Unit)? = null,
    always: (CryptchatRequest.OnUiThread.(Boolean) -> Unit)? = null,
    authenticate: Boolean = true
  ) {
    val headers = if (authenticate) authHeaders() else null
    val boundary = "--CryptchatFormBoundary" + CryptchatUtils.secureRandomHex(8)
    (headers ?: HashMap()).also {
      it["Content-Type"] = "multipart/form-data; boundary=$boundary"
    }
    val body = ByteArrayOutputStream().apply {
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

    CryptchatRequest(
      url = server.urlForPath(path),
      method = CryptchatRequest.Methods.POST,
      requestHeaders = headers
    ).apply {
      if (success != null) success(success)
      if (failure != null) failure(failure)
      if (always != null) always(always)
      headers {
        if (authenticate) {
          updateAuthToken(it)
          topUpEphemeralKeys(it)
        }
      }
      perform(body)
    }
  }

  fun request(
    method: CryptchatRequest.Methods,
    path: String,
    param: JSONObject? = null,
    success: (CryptchatRequest.OnUiThread.(JSONObject) -> Unit)? = null,
    failure: (CryptchatRequest.OnUiThread.(CryptchatRequest.ErrorMetadata) -> Unit)? = null,
    always: (CryptchatRequest.OnUiThread.(Boolean) -> Unit)? = null,
    authenticate: Boolean = true,
    async: Boolean = true
  ) {
    var url = server.urlForPath(path)
    val headers = if (authenticate) authHeaders() else null
    // Function parameters in Kotlin are final... let's work around that limitation
    // by declaring a local variable with the same name
    var param = param
    // Make the request body null in GET requests and convert params
    // to query string
    if (method == CryptchatRequest.Methods.GET && param != null) {
      val queryString = buildQueryString(param)
      if (queryString.isNotEmpty()) {
        url += if (url.indexOf('?') == -1) '?' else '&'
        url += queryString
      }
      param = null
    }
    CryptchatRequest(
      url = url,
      method = method,
      async = async,
      requestHeaders = headers
    ).apply {
      if (success != null) success(success)
      if (failure != null) failure(failure)
      if (always != null) always(always)
      headers {
        if (authenticate) {
          updateAuthToken(it)
          topUpEphemeralKeys(it)
          updateAdminStatus(it)
        }
      }
      if (param != null) perform(param) else perform()
    }
  }

  private fun updateAuthToken(headers: Map<String, String>) {
    val authToken = headers[AUTH_TOKEN_HEADER]
    if (authToken != null && authToken.isNotEmpty()) {
      server.authToken = authToken
      Cryptchat.db(context).servers().update(server)
    }
  }

  private fun authHeaders() : HashMap<String, String> {
    return HashMap<String, String>().also {
      it[AUTH_TOKEN_HEADER] = server.authToken
      it[AUTH_USER_ID_HEADER] = server.userId.toString()
    }
  }

  private fun buildQueryString(params: JSONObject) : String {
    val builder = StringBuilder()
    val iterator = params.keys().iterator()
    while (iterator.hasNext()) {
      val key = iterator.next()
      builder.append(key)
      builder.append('=')
      builder.append(params[key].toString())
      if (iterator.hasNext()) {
        builder.append('&')
      }
    }
    return builder.toString()
  }

  private fun topUpEphemeralKeys(headers: Map<String, String>) {
    val keysCount = headers[REMAINING_KEYS_COUNT].let {
      try {
        it?.toInt()
      } catch (ex: NumberFormatException) {
        null
      }
    }
    if (keysCount != null && keysCount < 500) {
      SupplyEphemeralKeysWorker.enqueue(server.id, 500, context)
    }
  }

  private fun updateAdminStatus(headers: Map<String, String>) {
    val isAdmin = headers[IS_ADMIN_HEADER]?.toLowerCase() == "true"
    if (isAdmin != server.isAdmin) {
      server.isAdmin = isAdmin
      Cryptchat.db(context).servers().update(server)
    }
  }
}