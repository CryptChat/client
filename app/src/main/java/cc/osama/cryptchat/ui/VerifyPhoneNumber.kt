package cc.osama.cryptchat.ui

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.util.Log.w
import androidx.constraintlayout.widget.ConstraintLayout
import cc.osama.cryptchat.R
import cc.osama.cryptchat.Cryptchat
import cc.osama.cryptchat.CryptchatSecurity
import cc.osama.cryptchat.CryptchatServer
import cc.osama.cryptchat.db.EphemeralKey
import cc.osama.cryptchat.db.Server
import kotlinx.android.synthetic.main.activity_verify_phone_number.*
import org.json.JSONArray
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
      submitButtonHandler()
    }
  }

  private fun submitButtonHandler() {
    val id = intent.extras?.getInt("id")
    val address = intent.extras?.getString("address")
    val token = verificationCodeField.text.toString()
    if (id == null || address == null) {
      return
    }
    val param = JSONObject()
    val keyPair = CryptchatSecurity.genKeyPair()
    param.put("id", id)
    param.put("verification_token", token)
    param.put("identity_key", keyPair.publicKey.toString())
    CryptchatServer(applicationContext, address).post(
      path = "/register.json",
      param = param,
      success = {
        val userId = (it["id"] as? Int)?.toLong()
        if (userId != null) {
          val db = Cryptchat.db(applicationContext)
          db.asyncExec(
            task = {
              val server = Server(
                address = address,
                name = "SErVeR!&#_X",
                userId = userId,
                publicKey = keyPair.publicKey.toString(),
                privateKey = keyPair.privateKey.toString()
              )
              val serverId = db.servers().add(server)
              finishUpRegistration(address, serverId, userId)
            }, onProgress = {}, after = {}
          )
        }
        w("USERID", userId.toString())
      },
      failure = {
        w("FAILUUUURE", it.javaClass.toString())
      }
    )
  }

  private fun finishUpRegistration(address: String, serverId: Long, userId: Long) {
    val keysJsonArray = JSONArray()
    val ephemeralKeyList = mutableListOf<EphemeralKey>()
    for (i in 1..1000) {
      val keyPair = CryptchatSecurity.genKeyPair()
      keysJsonArray.put(keyPair.publicKey.toString())
      ephemeralKeyList.add(
        EphemeralKey(
          serverId = serverId,
          publicKey = keyPair.publicKey.toString(),
          privateKey = keyPair.privateKey.toString()
        )
      )
    }
    val params = JSONObject()
    params.put("user_id", userId)
    params.put("keys", keysJsonArray)
    CryptchatServer(applicationContext, address).post(
      path = "/ephemeral-keys.json",
      param = params,
      success = {
        val db = Cryptchat.db(applicationContext)
        val ids = db.ephemeralKeys().addMany(ephemeralKeyList)
        w("DATABASETEST1", ids.toString())
        w("DATABASETEST1", ids.size.toString())
        w("DATABASETEST1", ids.javaClass.toString())
      },
      failure = {
        w("CONNECTION_FAILURE", it.javaClass.toString())
      }
    )
  }
}
