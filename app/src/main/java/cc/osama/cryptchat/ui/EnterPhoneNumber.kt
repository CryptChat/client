package cc.osama.cryptchat.ui

import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log.*
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import cc.osama.cryptchat.*
import kotlinx.android.synthetic.main.activity_enter_phone_number.*
import org.json.JSONObject
import java.lang.StringBuilder
import kotlin.collections.ArrayList

class EnterPhoneNumber : AppCompatActivity() {
  companion object {
    fun createIntent(address: String, context: Context) : Intent {
      return Intent(context, EnterPhoneNumber::class.java).apply {
        putExtra("address", address)
      }
    }
  }

  private lateinit var address: String
  private class SpinnerCountry(val country: String, val code: String, val display: String) {
    override fun toString() : String {
      return display
    }
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    address = intent.extras?.getString("address") as String
    setContentView(R.layout.activity_enter_phone_number)
    setSupportActionBar(enterPhoneNumberToolbar)
    supportActionBar?.setDisplayHomeAsUpEnabled(true)
    val countryCodes = ArrayList<SpinnerCountry>()
    CountryCodeMapping.countries.forEach { country ->
      val code = CountryCodeMapping.codeFor(country)
      val first = country.codePointAt(0)
      val second = country.codePointAt(1)
      // See this article to understand what's happening here
      // https://blog.emojipedia.org/emoji-flags-explained/
      val codePoints = arrayOf(0x1F1A5 + first, 0x1F1A5 + second).toIntArray()
      val flag = String(codePoints, 0, 2)
      countryCodes.add(SpinnerCountry(country, code, "$flag   $code"))
    }
    ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, countryCodes).also { adapter ->
      adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
      countryCodeField.adapter = adapter
    }
    countryCodeField.setSelection(0)
    phoneNumberField.addTextChangedListener(CryptchatTextWatcher(after = { s ->
      if (s == null) return@CryptchatTextWatcher
      val format = CountryCodeMapping.formatFor((countryCodeField.selectedItem as SpinnerCountry).country)
      var shouldReformat = s.length > format.length
      if (!shouldReformat) {
        s.toString().forEachIndexed { index, c ->
          shouldReformat = shouldReformat ||
            (c != CountryCodeMapping.SPACE && format[index] == CountryCodeMapping.SPACE) ||
            (c == CountryCodeMapping.SPACE && format[index] != CountryCodeMapping.SPACE)
        }
      }
      if (!shouldReformat) {
        updateSubmitButtonStatus(s.toString(), format)
        return@CryptchatTextWatcher
      }
      val maxLength = format.replace(CountryCodeMapping.SPACE.toString(), "").length
      var entered = s.toString().replace(Regex("[^\\d]+"), "")
      if (entered.length > maxLength) {
        entered = entered.substring(0, maxLength)
      }
      val builder = StringBuilder()
      var formatIndex = 0
      entered.forEach { c ->
        if (format[formatIndex] == CountryCodeMapping.SPACE) {
          builder.append(CountryCodeMapping.SPACE)
          builder.append(c)
          formatIndex++
        } else {
          builder.append(c)
        }
        formatIndex++
      }
      val newValue = builder.toString()
      s.replace(0, s.length, newValue)
      updateSubmitButtonStatus(newValue, format)
    }))
    countryCodeField.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
      override fun onNothingSelected(parent: AdapterView<*>?) {}
      override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
        phoneNumberField.text.clear()
        val format = CountryCodeMapping.formatFor(countryCodes[position].country)
        phoneNumberField.setText(format.replace(Regex("[^\\d]+"), ""))
      }
    }
    enterPhoneNumberSubmitButton.setOnClickListener {
      val code = CountryCodeMapping.codeFor((countryCodeField.selectedItem as SpinnerCountry).country)
      val number = phoneNumberField.text.toString().replace(Regex("[^\\d]+"), "")
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
            AsyncExec.onUiThread {
              toggleErrorMessage()
              startActivity(
                VerifyPhoneNumber.createIntent(
                  id = id,
                  address = address,
                  senderId = senderId,
                  phoneNumber = CountryCodeMapping.formatNumber(code, number),
                  context = this@EnterPhoneNumber
                )
              )
            }
          } else {
            AsyncExec.onUiThread {
              d("EnterPhoneNumber", "Unexpected condition in registerAtServer success. json=$json")
              toggleErrorMessage(resources.getString(R.string.registration_id_missing, id.toString()))
            }
          }
        },
        failure = { error ->
          AsyncExec.onUiThread {
            e("EnterPhoneNumber", "registerAtServer failure. $error", error.originalError)
            if (error.serverMessages.isNotEmpty()) {
              toggleErrorMessage(error.serverMessages.joinToString("\n"))
            } else {
              // TODO(Generic error)
            }
          }
        }
      )
    }
  }

  override fun onOptionsItemSelected(item: MenuItem): Boolean {
    if (item.itemId == android.R.id.home) {
      onBackPressed()
    } else {
      return super.onOptionsItemSelected(item)
    }
    return true
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

  private fun updateSubmitButtonStatus(newValue: String, format: String) {
    var enableButton = newValue.length == format.length
    if (enableButton) {
      newValue.forEachIndexed { index, c ->
        enableButton = enableButton && (
          (c == CountryCodeMapping.SPACE && format[index] == CountryCodeMapping.SPACE) ||
          (c != CountryCodeMapping.SPACE && format[index] != CountryCodeMapping.SPACE)
        )
      }
    }
    enterPhoneNumberSubmitButton.isEnabled = enableButton
  }
}
