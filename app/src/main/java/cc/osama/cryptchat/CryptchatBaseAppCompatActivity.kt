package cc.osama.cryptchat

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

abstract class CryptchatBaseAppCompatActivity : AppCompatActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    setTheme(Cryptchat.defaultTheme(applicationContext))
    super.onCreate(savedInstanceState)
  }
}