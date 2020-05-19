package cc.osama.sechat.ui

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log.w
import cc.osama.sechat.R
import cc.osama.sechat.Sechat
import cc.osama.sechat.SechatServer
import cc.osama.sechat.db.Server
import kotlinx.android.synthetic.main.activity_verify_phone_number.*
import org.json.JSONObject

class VerifyPhoneNumber : AppCompatActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_verify_phone_number)
    verificationCodeField.addTextChangedListener(object : TextWatcher {
      override fun afterTextChanged(s: Editable?) {}
      override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
      override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
        verificationCodeSubmit.isEnabled = s != null && s.length == 8
      }
    })
    verificationCodeSubmit.setOnClickListener {
      val id = intent.extras?.getInt("id")
      val address = intent.extras?.getString("address")
      val token = verificationCodeField.text.toString()
      if (id == null || address == null) {
        return@setOnClickListener
      }
      val param = JSONObject()
      param.put("id", id)
      param.put("verification_token", token)
      param.put("identity_key", "somekeygoeshere")
      SechatServer(applicationContext, address).post(
        path = "/register.json",
        param = param,
        success = {
          val userId = it["id"] as? Int
          if (userId != null) {
            val db = Sechat.db(applicationContext)
            db.asyncExec({
              val server = Server(address = address, name = "SErVeR!&#_X", userId = userId)
              db.server().add(server)
            })
          }
          w("USERID", userId.toString())
        },
        failure = {
          w("FAILUUUURE", it.javaClass.toString())
        }
      )
    }
  }
}
