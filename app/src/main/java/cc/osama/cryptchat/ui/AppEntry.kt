package cc.osama.cryptchat.ui

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Looper
import android.util.Log.w
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import cc.osama.cryptchat.AsyncExec
import cc.osama.cryptchat.Cryptchat
import cc.osama.cryptchat.R
import cc.osama.cryptchat.db.Message
import cc.osama.cryptchat.worker.SyncMessagesWorker
import cc.osama.cryptchat.worker.SyncUsersWorker
import kotlinx.android.synthetic.main.activity_app_entry.*

class AppEntry : AppCompatActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    AsyncExec.run {
      Cryptchat.db(applicationContext).also { db ->
        db.servers().getAll().forEach { server ->
          SyncUsersWorker.enqueue(
            serverId = server.id,
            scheduleMessagesSync = false,
            context = applicationContext
          )
        }
      }
    }
    // Cryptchat.db(applicationContext).asyncExec({
      // w("USERRRR CRYPTCHAT", FirebaseInstanceId.getInstance().getToken("530989455642", "FCM"))
      // w("USERRRR SECHAT", FirebaseInstanceId.getInstance().getToken("108521922410", "FCM"))
    // })

    /** val senderIdKeyPair = ECKeyPair(
      "niPLt99JahABLoSBx3vZK7kUWCyrrsF0RcVE9GYl3QY=",
      "qPsfwm+sTovdsv1/LpzYYbHhYQo3/UDs8LltDy72amI="
    )
    val senderEphKeyPair = ECKeyPair(
      "XRAvrsGtmD1jOZECtWxcLSuLdzb0+z39AxTd1TzDi0I=",
      "GNoiVReevmNxx+2899XTf/aK98T/qHszI8UY+sA6TFQ="
    )

    val receiverIdKeyPair = ECKeyPair(
      "RaBn1dzEXYLk6RHWlletqLA/4wiWSKvVnbcMWJKxUms=",
      "QEqJ1ZRVsU5sF6V+WD9xpVqLFVN6j/9E5Q7KRyCufk4="
    )
    val receiverEphKeyPair = ECKeyPair(
      "1ODHZG1vgDvVKIIOtaT7yczTrd5RluQK49FP9h02HkY=",
      "QJX5IynS6BlyiYRYrJgNqGn1XT95iQKXjGhw8zaimGI="
    )

    val message = "THIS IS VERY SECRET"
    val res = CryptchatSecurity().encrypt(
      message = message,
      senderIdKeyPair = senderIdKeyPair,
      receiverIdPubKey = receiverIdKeyPair.publicKey,
      receiverEphPubKey = receiverEphKeyPair.publicKey
    )
    val aft = CryptchatSecurity().decrypt(
      CryptchatSecurity.DecryptionInput(
        iv = res.iv,
        mac = res.mac,
        ciphertext = res.ciphertext,
        senderIdPubKey = senderIdKeyPair.publicKey,
        senderEphPubKey = res.senderEphPubKey,
        receiverIdKeyPair = receiverIdKeyPair,
        receiverEphPriKey = receiverEphKeyPair.privateKey
      )
    )
    return
    **/
    Cryptchat.db(applicationContext).also { db ->
      AsyncExec.run {
        val servers = db.servers().getAll()
        val intent: Intent
        if (servers.isEmpty()) {
          intent = Intent(this, EnterServerAddress::class.java)
        } else {
          intent = Intent(this, ServerUsersList::class.java)
          intent.putExtra("serverId", servers[0].id)
          intent.putExtra("server", servers[0])
        }
        it.execMainThread {
          startActivity(intent)
          finish()
        }
      }
    }
  }
}
