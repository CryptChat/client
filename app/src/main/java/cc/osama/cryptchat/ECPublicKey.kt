package cc.osama.cryptchat

import android.util.Base64

class ECPublicKey(private val publicKey: ByteArray) {
  override fun toString(): String {
    return Base64.encodeToString(publicKey, Base64.DEFAULT)
  }
  fun toByteArray(): ByteArray = publicKey
}