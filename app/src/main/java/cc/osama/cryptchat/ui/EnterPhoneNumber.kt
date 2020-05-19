package cc.osama.cryptchat.ui

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log.w
import android.view.View
import android.widget.ArrayAdapter
import cc.osama.cryptchat.R
import cc.osama.cryptchat.CryptchatServer
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
    phoneNumberField.addTextChangedListener(object : TextWatcher {
      override fun afterTextChanged(s: Editable?) {}
      override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
      override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
        goPhoneNumber.isEnabled = s != null && s.isNotEmpty()
      }
    })
    goPhoneNumber.setOnClickListener {
      val address = intent.extras?.getString("address").toString()
      val code = countryCodeField.selectedItem.toString()
      val number = phoneNumberField.text.toString()
      val param = JSONObject()
      param.put("country_code", code)
      param.put("phone_number", number)
      toggleErrorMessage()
      CryptchatServer(applicationContext, address).post(
        path = "/register.json",
        param = param,
        success = {
          val id = it["id"] as? Int
          if (id != null) {
            toggleErrorMessage()
            val intent = Intent(this, VerifyPhoneNumber::class.java)
            intent.putExtra("id", id)
            intent.putExtra("address", address)
            startActivity(intent)
          } else {
            toggleErrorMessage(resources.getString(R.string.registration_id_missing,id?.toString() ?: "NULL"))
          }
        },
        failure = {
          w("TESXXTSTS", it.javaClass.toString())
        }
      )
      w("TXXXXXXST", countryCodeField.selectedItem.toString())
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
