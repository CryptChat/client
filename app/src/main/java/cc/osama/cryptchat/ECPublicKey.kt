package cc.osama.cryptchat

import android.util.Base64
import java.io.Serializable

class ECPublicKey(private val publicKey: ByteArray) : Serializable {
  constructor(key: String) : this(Base64.decode(key, Base64.DEFAULT))

  class EphPubKeyFromServer(
    private val stringKey: String,
    val key: ECPublicKey = ECPublicKey(stringKey),
    val idOnUserDevice: Long
  )

  override fun toString(): String {
    return Base64.encodeToString(publicKey, Base64.DEFAULT)
  }
  fun toByteArray(): ByteArray = publicKey
}