package cc.osama.cryptchat

class EncryptionPackage(
  val AliceIdentityPrivateKey: ECPrivateKey,
  val AliceIdentityPublicKey: ECPublicKey,
  val BobIdentityPublicKey: ECPublicKey,
  val BobEphemeralPublicKey: ECPublicKey
) {
}