package cc.osama.cryptchat.ui

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log.w
import cc.osama.cryptchat.*
import cc.osama.cryptchat.db.EphemeralKey
import cc.osama.cryptchat.db.Server
import cc.osama.cryptchat.worker.SyncUsersWorker
import com.google.firebase.iid.FirebaseInstanceId
import kotlinx.android.synthetic.main.activity_verify_phone_number.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

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
    val senderId = intent.extras?.getString("senderId")
    val token = verificationCodeField.text.toString()
    if (id == null || address == null || senderId == null) {
      return
    }
    AsyncExec.run {
      val instanceId: String? = try {
        FirebaseInstanceId.getInstance().getToken(senderId, "FCM")
      } catch (ex: IOException) {
        null
      }
      val keyPair = CryptchatSecurity.genKeyPair()
      val params = JSONObject().also { params ->
        params.put("id", id)
        params.put("instance_id", instanceId)
        params.put("identity_key", keyPair.publicKey.toString())
        params.put("verification_token", token)
      }
      CryptchatServer.registerAtServer(
        address = address,
        params = params,
        success = {
          val userId = CryptchatUtils.toLong(it["id"])
          val authToken = it["auth_token"] as? String
          if (userId != null && authToken != null) {
            val server = addServerToDatabase(address, userId, keyPair, senderId, authToken, instanceId)
            supplyEphemeralKeys(server)
            SyncUsersWorker.enqueue(serverId = server.id, context = applicationContext)
            onUiThread {
              startActivity(ServerUsersList.createIntent(server, applicationContext))
            }
          }
          w("USERID", userId.toString())
        },
        failure = {
          w("FAILUUUURE", it.toString())
        }
      )
    }
  }

  private fun addServerToDatabase(address: String, userId: Long, keyPair: ECKeyPair, senderId: String, authToken: String, instanceId: String?) : Server {
    return Cryptchat.db(applicationContext).servers().add(Server(
      address = address,
      name = "SErVeR!&#_X", // TODO: FIX THIS!
      userId = userId,
      keyPair = keyPair,
      senderId = senderId,
      authToken = authToken,
      instanceId = instanceId,
      userName = null
    ))
  }

  private fun supplyEphemeralKeys(server: Server) {
    val ephemeralKeysList = mutableListOf<EphemeralKey>()
    for (i in 1..500) {
      val keyPair = CryptchatSecurity.genKeyPair()
      ephemeralKeysList.add(
        EphemeralKey(
          serverId = server.id,
          publicKey = keyPair.publicKey.toString(),
          privateKey = keyPair.privateKey.toString()
        )
      )
    }
    val db = Cryptchat.db(applicationContext)
    AsyncExec.run {
      val ids = db.ephemeralKeys().addMany(ephemeralKeysList)
      val keysList = db.ephemeralKeys().findByIds(ids)
      val jsonArray = JSONArray()
      keysList.forEach { key ->
        val jsonKey = JSONObject()
        jsonKey.put("id", key.id)
        jsonKey.put("key", key.publicKey)
        jsonArray.put(jsonKey)
      }
      val params = JSONObject()
      params.put("keys", jsonArray)
      CryptchatServer(applicationContext, server).request(
        method = CryptchatRequest.Methods.POST,
        path = "/ephemeral-keys.json",
        param = params,
        failure = {
          db.ephemeralKeys().deleteMany(ephemeralKeysList)
        }
      )
    }
  }
}
