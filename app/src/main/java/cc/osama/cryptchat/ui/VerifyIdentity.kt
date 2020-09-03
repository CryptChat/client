package cc.osama.cryptchat.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import cc.osama.cryptchat.CryptchatSecurity
import cc.osama.cryptchat.R
import cc.osama.cryptchat.db.Server
import cc.osama.cryptchat.db.User
import kotlinx.android.synthetic.main.activity_verify_identity.*
import java.lang.StringBuilder

class VerifyIdentity: AppCompatActivity() {
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
    val codes = CryptchatSecurity.genVerificationCode(first, second) // contains 16 strings
    val builder = StringBuilder()
    for (i in 0..3) {
      for (j in 0..3) {
        if (j == 0) builder.append(" | ")
        builder.append(codes[i * 4 + j])
        builder.append(" | ")
      }
      if (i != 3) builder.append("\n")
    }
    verificationCodeHolder.text = builder.toString()
  }
}
