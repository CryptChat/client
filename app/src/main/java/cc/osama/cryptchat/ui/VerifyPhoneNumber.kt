package cc.osama.cryptchat.ui

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
import kotlin.math.floor

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
      fetchServerMembers("http://172.18.170.181:3000", 1, 2)
      //submitButtonHandler()
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
        val server = Server(
          address = address,
          name = "SErVeR!&#_X",
          userId = userId,
          keyPair = keyPair
        )
        val serverId = db.servers().add(server)
        fetchServerMembers(address, serverId, userId)
        finishUpRegistration(address, serverId, userId)
      }
    )
  }

  private fun fetchServerMembers(address: String, serverId: Long, userId: Long) {
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
                    serverId = serverId,
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
          })
        } else {
          w("USERSSS2", it["users"].javaClass.toString())
          w("USERSSS2", it["users"].toString())
        }
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
