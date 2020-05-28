package cc.osama.cryptchat

import java.io.Serializable

class ECKeyPair(val publicKey: ECPublicKey, val privateKey: ECPrivateKey) : Serializable {
  constructor(
    publicKey: ByteArray,
    privateKey: ByteArray
  ) : this(
    publicKey = ECPublicKey(publicKey),
    privateKey = ECPrivateKey(privateKey)
  )

  constructor(
    publicKey: String,
    privateKey: String
  ) : this(
    publicKey = ECPublicKey(publicKey),
    privateKey = ECPrivateKey(privateKey)
  )
}