package cc.osama.cryptchat

class ECKeyPair(val publicKey: ECPublicKey, val privateKey: ECPrivateKey) {
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