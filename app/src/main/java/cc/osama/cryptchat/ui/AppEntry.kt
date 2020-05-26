package cc.osama.cryptchat.ui

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log.w
import cc.osama.cryptchat.Cryptchat
import cc.osama.cryptchat.CryptchatFirebaseMessagingService
import com.google.firebase.iid.FirebaseInstanceId

class AppEntry : AppCompatActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val db = Cryptchat.db(applicationContext)
    db.asyncExec({
      val servers = db.servers().getAll()
      val intent: Intent
      if (servers.isEmpty()) {
        intent = Intent(this, EnterServerAddress::class.java)
      } else {
        intent = Intent(this, ServerMembers::class.java)
        intent.putExtra("serverId", servers[0].id)
      }
      it.execOnUIThread {
        startActivity(intent)
        finish()
      }
    })
  }
}
