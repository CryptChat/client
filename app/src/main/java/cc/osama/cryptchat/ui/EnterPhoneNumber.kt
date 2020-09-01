package cc.osama.cryptchat.ui

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log.*
import android.view.View
import android.widget.ArrayAdapter
import cc.osama.cryptchat.R
import cc.osama.cryptchat.CryptchatServer
import cc.osama.cryptchat.CryptchatTextWatcher
import cc.osama.cryptchat.CryptchatUtils
import kotlinx.android.synthetic.main.activity_enter_phone_number.*
import org.json.JSONObject

class EnterPhoneNumber : AppCompatActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_enter_phone_number)
    val countryCodes = ArrayList<String>()
    countryCodes.addAll(resources.getStringArray(R.array.countries_codes))
    ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, countryCodes).also { adapter ->
      adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
      countryCodeField.adapter = adapter
    }
    countryCodeField.setSelection(0)
    phoneNumberField.addTextChangedListener(CryptchatTextWatcher(on = { charSequence, _, _, _ ->
      goPhoneNumber.isEnabled = charSequence != null && charSequence.isNotEmpty()
    }))
    goPhoneNumber.setOnClickListener {
      val address = intent.extras?.getString("address").toString()
      val code = countryCodeField.selectedItem.toString()
      val number = phoneNumberField.text.toString()
      val params = JSONObject().also { params ->
        params.put("country_code", code)
        params.put("phone_number", number)
      }
      toggleErrorMessage()
      CryptchatServer.registerAtServer(
        async = true,
        address = address,
        params = params,
        success = { json ->
          val id = json.optLong("id", -999_999)
          val senderId = CryptchatUtils.jsonOptString(json, "sender_id")
          if (id != (-999_999).toLong() && senderId != null) {
            onUiThread {
              toggleErrorMessage()
              startActivity(
                VerifyPhoneNumber.createIntent(
                  id = id,
                  address = address,
                  senderId = senderId,
                  context = applicationContext
                )
              )
            }
          } else {
            d("EnterPhoneNumber", "Unexpected condition in registerAtServer success. json=$json")
            onUiThread {
              toggleErrorMessage(resources.getString(R.string.registration_id_missing, id.toString()))
            }
          }
        },
        failure = { error ->
          e("EnterPhoneNumber", "registerAtServer failure. $error", error.originalError)
        }
      )
    }
  }

  private fun toggleErrorMessage(message: String? = null) {
    if (message != null) {
      errorPlaceholder.visibility = View.VISIBLE
      errorPlaceholder.text = message
    } else {
      errorPlaceholder.visibility = View.INVISIBLE
      errorPlaceholder.text = ""
    }
  }
}
