package cc.osama.cryptchat.ui

import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log.*
import cc.osama.cryptchat.*
import cc.osama.cryptchat.db.EphemeralKey
import cc.osama.cryptchat.db.Server
import cc.osama.cryptchat.db.User
import cc.osama.cryptchat.worker.SupplyEphemeralKeysWorker
import cc.osama.cryptchat.worker.SyncUsersWorker
import com.google.firebase.iid.FirebaseInstanceId
import kotlinx.android.synthetic.main.activity_verify_phone_number.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

class VerifyPhoneNumber : AppCompatActivity() {
  companion object {
    fun createIntent(id: Long, address: String, senderId: String, context: Context) : Intent {
      return Intent(context, VerifyPhoneNumber::class.java).also {
        it.putExtra("id", id)
        it.putExtra("address", address)
        it.putExtra("senderId", senderId)
      }
    }
  }

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
    val id = intent.extras?.getLong("id")
    val address = intent.extras?.getString("address")
    val senderId = intent.extras?.getString("senderId")
    val token = verificationCodeField.text.toString()
    if (id == null || address == null || senderId == null) {
      d("VerifyPhoneNumber", "weird condition. id=$id, address=$address, senderId=$senderId.")
      return
    }
    AsyncExec.run {
      val instanceId: String? = try {
        FirebaseInstanceId.getInstance().getToken(senderId, "FCM")
      } catch (ex: IOException) {
        d("VerifyPhoneNumber", "FirebaseInstanceId exception", ex)
        null
      }
      val keyPair = CryptchatSecurity.genKeyPair()
      val params = JSONObject().apply {
        put("id", id)
        put("instance_id", instanceId)
        put("identity_key", keyPair.publicKey.toString())
        put("verification_token", token)
      }
      CryptchatServer.registerAtServer(
        async = false,
        address = address,
        params = params,
        success = { json ->
          val userId = json.optLong("id", -1)
          val authToken = CryptchatUtils.jsonOptString(json, "auth_token")
          val serverName = CryptchatUtils.jsonOptString(json, "server_name").let {
            if (it != null && it.isNotEmpty()) it else "Cryptchat Server"
          }
          if (userId != (-1).toLong() && authToken != null) {
            val server = Cryptchat.db(applicationContext).servers().add(
              Server(
                address = address,
                name = serverName,
                userId = userId,
                keyPair = keyPair,
                senderId = senderId,
                authToken = authToken,
                instanceId = instanceId,
                userName = null
              )
            )
            SupplyEphemeralKeysWorker.enqueue(serverId = server.id, batchSize = 500, context = applicationContext)
            SyncUsersWorker.enqueue(serverId = server.id, context = applicationContext)
            onUiThread {
              startActivity(ServerUsersList.createIntent(server, this@VerifyPhoneNumber))
            }
          } else {
            e(
              "VerifyPhoneNumber",
              "verification success callback weird condition. userId=$userId, authToken=$authToken, json=$json."
            )
          }
        },
        failure = { error ->
          e("VerifyPhoneNumber", "verification requests failed. $error", error.originalError)
        }
      )
    }
  }
}
