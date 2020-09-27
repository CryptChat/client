package cc.osama.cryptchat.ui

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import cc.osama.cryptchat.*

class AppEntry : AppCompatActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    Cryptchat.db(applicationContext).also { db ->
      AsyncExec.run {
        val servers = db.servers().getAll()
        // startActivity(AdminWebView.createIntent(servers[0], this))
        // return@run
        val intent = if (servers.isEmpty() && false) {
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
