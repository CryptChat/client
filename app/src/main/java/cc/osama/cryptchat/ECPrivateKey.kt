package cc.osama.cryptchat

import android.util.Base64
import java.io.Serializable

class ECPrivateKey(private val privateKey: ByteArray) : Serializable {
  constructor(key: String) : this(Base64.decode(key, Base64.DEFAULT))

  override fun toString(): String {
    return Base64.encodeToString(privateKey, Base64.DEFAULT)
  }

  fun toByteArray(): ByteArray = privateKey
}