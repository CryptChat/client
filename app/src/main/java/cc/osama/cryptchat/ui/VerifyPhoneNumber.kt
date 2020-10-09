package cc.osama.cryptchat.ui

import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log.*
import android.view.KeyEvent
import android.view.MenuItem
import android.widget.EditText
import cc.osama.cryptchat.*
import cc.osama.cryptchat.db.Server
import cc.osama.cryptchat.worker.SupplyEphemeralKeysWorker
import cc.osama.cryptchat.worker.SyncUsersWorker
import com.google.firebase.iid.FirebaseInstanceId
import kotlinx.android.synthetic.main.activity_verify_phone_number.*
import org.json.JSONObject
import java.io.IOException
import java.lang.StringBuilder
import kotlin.properties.Delegates

class VerifyPhoneNumber : AppCompatActivity() {
  companion object {
    private const val TOKEN_SIZE = 8

    fun createIntent(id: Long, address: String, senderId: String, phoneNumber: String, context: Context) : Intent {
      return Intent(context, VerifyPhoneNumber::class.java).also {
        it.putExtra("id", id)
        it.putExtra("address", address)
        it.putExtra("senderId", senderId)
        it.putExtra("phoneNumber", phoneNumber)
      }
    }
  }

  private lateinit var address: String
  private lateinit var senderId: String
  private lateinit var phoneNumber: String
  private lateinit var fields: Array<EditText>
  private var id by Delegates.notNull<Long>()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    id = intent.extras?.getLong("id") as Long
    address = intent.extras?.getString("address") as String
    senderId = intent.extras?.getString("senderId") as String
    phoneNumber = intent.extras?.getString("phoneNumber") as String

    setContentView(R.layout.activity_verify_phone_number)
    setSupportActionBar(verifyPhoneNumberToolbar)
    supportActionBar?.setDisplayHomeAsUpEnabled(true)
    verificationCodeSubmit.setOnClickListener {
      submitButtonHandler()
    }
    fields = arrayOf(
      verificationDigit1,
      verificationDigit2,
      verificationDigit3,
      verificationDigit4,
      verificationDigit5,
      verificationDigit6,
      verificationDigit7,
      verificationDigit8
    )
    verificationCodeSubmit.isEnabled = getVerificationToken().length == TOKEN_SIZE
    fields.forEachIndexed { index, editText ->
      editText.addTextChangedListener(CryptchatTextWatcher(
        before = { _, _, _, after ->
          if (after == 1 && index + 1 < fields.size) {
            fields[index + 1].requestFocus()
          }
        },
        after = {
          verificationCodeSubmit.isEnabled = getVerificationToken().length == TOKEN_SIZE
        }
      ))
      editText.setOnKeyListener { _, keyCode, _ ->
        if (keyCode == KeyEvent.KEYCODE_DEL && index > 0 && editText.length() == 0) {
          fields[index - 1].requestFocus()
          return@setOnKeyListener true
        }
        return@setOnKeyListener false
      }
    }
    verifyPhoneNumberTipHolder.text = resources.getString(R.string.verify_phone_number_view_tip, phoneNumber)
    fields[0].requestFocus()
  }

  override fun onOptionsItemSelected(item: MenuItem): Boolean {
    if (item.itemId == android.R.id.home) {
      onBackPressed()
    } else {
      return super.onOptionsItemSelected(item)
    }
    return true
  }

  private fun getVerificationToken() : String {
    val builder = StringBuilder()
    fields.forEach {
      builder.append(it.text.toString())
    }
    return builder.toString()
  }

  private fun submitButtonHandler() {
    val token = getVerificationToken()
    AsyncExec.run(AsyncExec.Companion.Threads.Network) {
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
