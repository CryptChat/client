package cc.osama.cryptchat.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import cc.osama.cryptchat.CryptchatBaseAppCompatActivity
import cc.osama.cryptchat.CryptchatSecurity
import cc.osama.cryptchat.R
import cc.osama.cryptchat.db.Server
import cc.osama.cryptchat.db.User
import kotlinx.android.synthetic.main.activity_verify_identity.*

class VerifyIdentity: CryptchatBaseAppCompatActivity() {
  private lateinit var user: User
  private lateinit var server: Server

  companion object {
    fun createIntent(user: User, server: Server, context: Context) : Intent {
      return Intent(context, VerifyIdentity::class.java).apply {
        putExtra("user", user)
        putExtra("server", server)
      }
    }
  }
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_verify_identity)
    user = intent.extras?.get("user") as User
    server = intent.extras?.get("server") as Server
    val (first, second) = if (user.idOnServer < server.userId) {
      arrayOf(user.publicKey.toByteArray(), server.keyPair.publicKey.toByteArray())
    } else {
      arrayOf(server.keyPair.publicKey.toByteArray(), user.publicKey.toByteArray())
    }
    val words = CryptchatSecurity.generateMnemonicSentence(first, second)
    mnemonicWord1.text = words[0]
    mnemonicWord2.text = words[1]
    mnemonicWord3.text = words[2]
    mnemonicWord4.text = words[3]
    mnemonicWord5.text = words[4]
    mnemonicWord6.text = words[5]
    mnemonicWord7.text = words[6]
    mnemonicWord8.text = words[7]
    mnemonicWord9.text = words[8]
    mnemonicWord10.text = words[9]
    mnemonicWord11.text = words[10]
    mnemonicWord12.text = words[11]
    setSupportActionBar(verifyIdentityToolbar)
    supportActionBar?.setDisplayHomeAsUpEnabled(true)
    supportActionBar?.title = resources.getString(R.string.chat_view_menu_verify_identity)
  }

  override fun onOptionsItemSelected(item: MenuItem): Boolean {
    if (item.itemId == android.R.id.home) {
      onBackPressed()
    } else {
      return super.onOptionsItemSelected(item)
    }
    return true
  }
}
