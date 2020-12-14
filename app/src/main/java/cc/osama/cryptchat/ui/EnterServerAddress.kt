package cc.osama.cryptchat.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import cc.osama.cryptchat.*
import kotlinx.android.synthetic.main.activity_enter_server_address.*
import java.net.MalformedURLException
import java.net.URL

class EnterServerAddress : CryptchatBaseAppCompatActivity() {
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
    serverAddressInput.addTextChangedListener(CryptchatTextWatcher(after = { s ->
      addServerButton.isEnabled = s != null && s.isNotBlank()
    }))
    addServerButton.setOnClickListener {
      errorMessagePlaceholder.text = null
      changeElementsEnabledStatus(false)
      val address = canonicalAddress(serverAddressInput.text.toString().trim())
      if (address == null) {
        changeElementsEnabledStatus(true)
        return@setOnClickListener
      }
      validateServer(address)
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

  private fun validateServer(address: String) {
    CryptchatServer.checkAddress(
      address = address,
      success = { json ->
        val isCryptchatServer = json.optBoolean("is_cryptchat", false)
        if (!isCryptchatServer) {
          AsyncExec.onUiThread {
            errorMessagePlaceholder.text = resources.getString(R.string.not_a_cryptchat_server)
            changeElementsEnabledStatus(true)
          }
          return@checkAddress
        }
        val db = Cryptchat.db(applicationContext)
        val server = db.servers().findByAddress(address)
        AsyncExec.onUiThread {
          if (server == null) { // valid server
            startActivity(EnterPhoneNumber.createIntent(address, this@EnterServerAddress))
          } else {
            errorMessagePlaceholder.text = resources.getString(R.string.server_already_added)
            changeElementsEnabledStatus(true)
          }
        }
      },
      failure = {
        val errorId = if (it.statusCode in 300..499) {
          R.string.not_a_cryptchat_server
        } else {
          it.genericErrorId
        }
        AsyncExec.onUiThread {
          errorMessagePlaceholder.text = resources.getString(errorId)
          changeElementsEnabledStatus(true)
        }
      }
    )
  }

  private fun canonicalAddress(address: String?) : String? {
    try {
      val url = URL(address)
      if (url.protocol != "https") {
        errorMessagePlaceholder.text =
          resources.getString(R.string.disallowed_protocol, url.protocol ?: "<unknown>")
        return null
      }
      if (url.host == null || url.host.isBlank()) {
        errorMessagePlaceholder.text = resources.getString(R.string.invalid_address, address)
        return null
      }
      return if (url.authority.contains(Regex("\\d"))) {
        "http://${url.authority}"
      } else {
        "https://${url.host}"
      }
    } catch (err: MalformedURLException) {
      val errorMessage = err.message
      if (errorMessage == null || errorMessage.isEmpty()) {
        errorMessagePlaceholder.text = resources.getString(R.string.invalid_address, address)
        return null
      }
      return if (errorMessage.contains("no protocol", ignoreCase = true) ||
        errorMessage.contains("unknown protocol", ignoreCase = true)) {
        canonicalAddress("https://$address")
      } else {
        errorMessagePlaceholder.text = errorMessage
        null
      }
    }
  }
}
