package cc.osama.cryptchat.ui

import android.content.Intent
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
      val param = JSONObject().also { param ->
        param.put("id", id)
        param.put("instance_id", instanceId)
        param.put("identity_key", keyPair.publicKey.toString())
        param.put("verification_token", token)
      }
      CryptchatServer(applicationContext, address).post(
        path = "/register.json",
        param = param,
        success = {
          val userId = CryptchatUtils.toLong(it["id"])
          if (userId != null) {
            addServerToDatabase(address, userId, keyPair, senderId)
          }
          w("USERID", userId.toString())
        },
        failure = {
          w("FAILUUUURE", it.javaClass.toString())
        }
      )
    }
  }

  private fun addServerToDatabase(address: String, userId: Long, keyPair: ECKeyPair, senderId: String) {
    val db = Cryptchat.db(applicationContext)
    AsyncExec.run {
      val server = db.servers().add(Server(
        address = address,
        name = "SErVeR!&#_X",
        userId = userId,
        keyPair = keyPair,
        senderId = senderId
      ))
      supplyEphemeralKeys(address, server, userId)
      SyncUsersWorker.enqueue(serverId = server.id, context = applicationContext)
      it.execMainThread {
        startActivity(ServerUsersList.createIntent(server, applicationContext))
      }
    }
  }

  private fun supplyEphemeralKeys(address: String, server: Server, userId: Long) {
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
      params.put("user_id", userId)
      params.put("keys", jsonArray)
      CryptchatServer(applicationContext, address).post(
        path = "/ephemeral-keys.json",
        param = params,
        failure = {
          AsyncExec.run {
            db.ephemeralKeys().deleteMany(ephemeralKeysList)
          }
        }
      )
    }
  }
}
