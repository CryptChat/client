package cc.osama.cryptchat.ui

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Looper
import android.util.Log.d
import android.util.Log.w
import cc.osama.cryptchat.*
import org.json.JSONObject

class AppEntry : AppCompatActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    Cryptchat.db(applicationContext).also { db ->
      AsyncExec.run {
        val servers = db.servers().getAll()
        val intent = if (servers.isEmpty()) {
          Intent(this, EnterServerAddress::class.java)
        } else {
          // ServerUsersList.createIntent(
          //   servers[0],
          //   this
          // )
          ServersList.createIntent(this)
        }
        it.execMainThread {
          startActivity(intent)
          finish()
        }
      }
    }
  }
}
