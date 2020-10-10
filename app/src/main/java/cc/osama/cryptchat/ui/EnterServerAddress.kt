package cc.osama.cryptchat.ui

import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Menu
import android.view.MenuItem
import cc.osama.cryptchat.*
import com.android.volley.ClientError
import com.android.volley.NoConnectionError
import com.android.volley.ServerError
import kotlinx.android.synthetic.main.activity_enter_server_address.*
import java.net.MalformedURLException
import java.net.URL
import java.net.UnknownHostException

class EnterServerAddress : AppCompatActivity() {
  companion object {
    fun createIntent(context: Context) : Intent {
      return Intent(context, EnterServerAddress::class.java)
    }
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_enter_server_address)
    setSupportActionBar(enterServerAddressToolbar)
    supportActionBar?.setDisplayHomeAsUpEnabled(true)
    serverAddressInput.addTextChangedListener(object : TextWatcher {
      override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
      override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
        addServerButton.isEnabled = s != null && s.isNotEmpty()
      }
      override fun afterTextChanged(s: Editable?) {}
    })
    addServerButton.setOnClickListener {
      errorMessagePlaceholder.text = null
      changeElementsEnabledStatus(false)
      var address = serverAddressInput.text.toString().trim()
      var errorMessage: String?
      errorMessage = validateAddress(address)
      if (errorMessage != null && errorMessage.isNotEmpty()) {
        errorMessagePlaceholder.text = errorMessage
        changeElementsEnabledStatus(true)
        return@setOnClickListener
      }

      address = getCanonicalAddress(address)
      validateServer(address,
        onValid = {
          val db = Cryptchat.db(applicationContext)
          AsyncExec.run {
            val serverDao = db.servers()
            val server = serverDao.findByAddress(address)
            if (server == null) {
              startActivity(EnterPhoneNumber.createIntent(address, this))
            } else {
              errorMessage = resources.getString(R.string.server_already_added)
            }
            it.execMainThread {
              errorMessagePlaceholder.text = errorMessage
              changeElementsEnabledStatus(true)
            }
          }
        },
        onInvalid = {
          errorMessagePlaceholder.text = it
          changeElementsEnabledStatus(true)
        }
      )
    }
  }

  override fun onOptionsItemSelected(item: MenuItem): Boolean {
    when (item.itemId) {
      android.R.id.home -> {
        onBackPressed()
      }
      else -> {
        return super.onOptionsItemSelected(item)
      }
    }
    return true
  }

  private fun changeElementsEnabledStatus(status: Boolean) {
    addServerButton.isEnabled = status
    serverAddressInput.isEnabled = status
  }

  private fun validateServer(address: String, onValid: () -> Unit, onInvalid: (message: String) -> Unit) {
    return onValid()
    CryptchatServer.checkAddress(
      address = address,
      success = {
        val isCryptchat = it.optBoolean("is_cryptchat", false)
        onUiThread {
          if (isCryptchat) {
            onValid()
          } else {
            onInvalid(resources.getString(R.string.not_a_cryptchat_server))
          }
        }
      },
      failure = {
        val errorMessage = if (it.isUnknownHostError) {
          resources.getString(R.string.unknown_host)
        } else if (it.isClientError) {
          val responseCode = it.statusCode
          if (responseCode == 404) {
            resources.getString((R.string.not_a_cryptchat_server))
          } else {
            resources.getString(R.string.client_error_occurred, responseCode)
          }
        } else if (it.isNoConnectionError) {
          resources.getString(R.string.not_pointing_to_server)
        } else if (it.isServerError) {
          val responseCode = it.statusCode
          if (responseCode >= 500) {
            resources.getString(R.string.server_down)
          } else {
            resources.getString(R.string.server_error_occurred, responseCode)
          }
        } else {
          resources.getString(R.string.unknown_error_occurred, "${it.javaClass}")
        }
        onUiThread {
          onInvalid(errorMessage)
        }
      }
    )
  }

  private fun getCanonicalAddress(address: String): String {
    var adrs = address
    if (!adrs.matches(Regex("^https?://.+", setOf(RegexOption.IGNORE_CASE)))) {
      adrs = "https://$adrs"
    }
    val url = URL(adrs)
    /*************** UNCOMMENT THIS LINE **************/
    // return "https://${url.host}"
    return "http://${url.authority}"
  }

  private fun validateAddress(address: String): String? {
    if (address.isEmpty()) {
      return resources.getString(R.string.empty_address)
    }
    return try {
      val url = URL(address)
      if (listOf("https", "http").indexOf(url.protocol) == -1) {
        return resources.getString(R.string.unsupported_protocol, url.protocol ?: "<unknown>")
      }
      if (url.host == null || url.host.isEmpty()) {
        return resources.getString(R.string.invalid_address, address)
      }
      null
    } catch (err: MalformedURLException) {
      var errorMessage = err.message
      if (errorMessage == null || errorMessage.isEmpty()) {
        errorMessage = resources.getString(R.string.invalid_address, address)
      }
      if (errorMessage.contains("no protocol", ignoreCase = true)) {
        validateAddress("https://$address")
      } else {
        errorMessage
      }
    }
  }
}
