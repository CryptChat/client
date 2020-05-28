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
import cc.osama.cryptchat.db.User
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
      // fetchServerMembers("http://172.18.170.181:3000", 1, 2)
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
          addServerToDatabase(address, userId, keyPair)
        }
        w("USERID", userId.toString())
      },
      failure = {
        w("FAILUUUURE", it.javaClass.toString())
      }
    )
  }

  private fun addServerToDatabase(address: String, userId: Long, keyPair: ECKeyPair) {
    val db = Cryptchat.db(applicationContext)
    db.asyncExec(
      task = {
        val server = db.servers().add(Server(
          address = address,
          name = "SErVeR!&#_X",
          userId = userId,
          keyPair = keyPair
        ))
        supplyEphemeralKeys(address, server, userId)
        fetchServerMembers(address, server, userId)
      }
    )
  }

  private fun fetchServerMembers(address: String, server: Server, userId: Long) {
    CryptchatServer(applicationContext, address).get(
      path = "/sync/users.json",
      success = {
        val usersJsonArray = it["users"] as? JSONArray
        if (usersJsonArray != null) {
          val users = mutableListOf<User>()
          for (i in 0 until usersJsonArray.length()) {
            val userJson = usersJsonArray[i] as? JSONObject
            if (userJson != null) {
              val publicKey = if (userJson["identity_key"] as? String != null) ECPublicKey(userJson["identity_key"] as String) else null
              val countryCode = userJson["country_code"] as? String
              val phoneNumber = userJson["phone_number"] as? String
              val idOnServer = (userJson["id"] as? Int)?.toLong() ?: userJson["id"] as? Long
              val lastUpdatedAt = userJson["updated_at"] as? Double
              val name = userJson["name"] as? String
              if (publicKey != null &&
                countryCode != null &&
                phoneNumber != null &&
                idOnServer != null &&
                idOnServer != userId &&
                lastUpdatedAt != null
              ) {
                users.add(
                  User(
                    serverId = server.id,
                    publicKey = publicKey,
                    lastUpdatedAt = lastUpdatedAt,
                    phoneNumber = phoneNumber,
                    countryCode = countryCode,
                    idOnServer = idOnServer,
                    name = name
                  )
                )
              }
            }
          }
          val db = Cryptchat.db(applicationContext)
          db.asyncExec({
            db.users().addMany(users)
          }, after = {
            val intent = Intent(this, ServerUsersList::class.java)
            intent.putExtra("serverId", server.id)
            intent.putExtra("server", server)
            startActivity(intent)
          })
        } else {
          w("USERSSS2", it["users"].javaClass.toString())
          w("USERSSS2", it["users"].toString())
        }
      }
    )
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
    db.asyncExec({
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
          db.asyncExec({
            db.ephemeralKeys().deleteMany(ephemeralKeysList)
          })
        }
      )
    })
  }
}
