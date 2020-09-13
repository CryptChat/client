package cc.osama.cryptchat

import android.util.Base64
import org.whispersystems.curve25519.Curve25519
import java.io.ByteArrayOutputStream
import java.lang.Exception
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.math.*

class CryptchatSecurity {
  class BadMac(message: String) : Exception(message)

  data class EncryptionOutput(
    var iv: String,
    var mac: String,
    var ciphertext: String,
    val senderEphPubKey: ECPublicKey?
  )

  data class DecryptionInput(
    val iv: String,
    val mac: String,
    val ciphertext: String,
    val senderIdPubKey: ECPublicKey,
    val senderEphPubKey: ECPublicKey?,
    val receiverIdKeyPair: ECKeyPair,
    val receiverEphPriKey: ECPrivateKey?
  )

  companion object {
    fun genKeyPair(): ECKeyPair {
      val keyPair = Curve25519.getInstance(Curve25519.BEST).generateKeyPair()
      val public = ECPublicKey(keyPair.publicKey)
      val private = ECPrivateKey(keyPair.privateKey)
      return ECKeyPair(public, private)
    }

    // implementation heavily inspired from
    // https://github.com/bitcoinj/bitcoinj/blob/master/core/src/main/java/org/bitcoinj/crypto/MnemonicCode.java#L196
    fun generateMnemonicSentence(firstParty: ByteArray, secondParty: ByteArray) : Array<String> {
      val sha = MessageDigest.getInstance("SHA-256")

      val entropy = sha.digest(firstParty + secondParty).copyOfRange(0, 16)
      val entropyBits = CryptchatUtils.bytesToBits(entropy)

      val hash = sha.digest(entropy)
      val hashBits = CryptchatUtils.bytesToBits(hash)

      val extraChecksumBitsLength = entropyBits.size / 32
      val allBits = BooleanArray(entropyBits.size + extraChecksumBitsLength)
      System.arraycopy(entropyBits, 0, allBits, 0, entropyBits.size)
      System.arraycopy(hashBits, 0, allBits, entropyBits.size, extraChecksumBitsLength)
      return Array(allBits.size / 11) { i ->
        var index = 0
        for (j in 0 until 11) {
          index = index shl 1
          if (allBits[i * 11 + j]) {
            index = index or 1
          }
        }
        Dictionary.words[index]
      }
    }
  }

  fun encrypt(
    message: String,
    senderIdKeyPair: ECKeyPair,
    receiverIdPubKey: ECPublicKey,
    receiverEphPubKey: ECPublicKey?
  ) : EncryptionOutput {
    val ss1 = calculateAgreement(receiverIdPubKey, senderIdKeyPair.privateKey)
    val stream = ByteArrayOutputStream()
    stream.write(ss1)
    val senderEphKeyPair: ECKeyPair?
    if (receiverEphPubKey != null) {
      senderEphKeyPair = genKeyPair()
      val ss2 = calculateAgreement(receiverEphPubKey, senderEphKeyPair.privateKey)
      stream.write(ss2)
    } else {
      senderEphKeyPair = null
    }
    val master = stream.toByteArray()
    val salt = ByteArray(32)
    val info = "Cryptchat".toByteArray()
    val prk = extract(salt, master)
    val derived = expand(prk, info, 64)

    val cipherKeyBytes = derived.copyOfRange(0, 32)
    val ivBytes = ByteArray(16)
    SecureRandom().nextBytes(ivBytes)
    val cipherBytes = toCiphertextBytes(
      plaintextBytes = message.toByteArray(),
      ivBytes = ivBytes,
      cipherKeyBytes = cipherKeyBytes
    )

    val macKeyBytes = derived.copyOfRange(32, 64)
    val mac = getMac(
      cipherBytes = cipherBytes + ivBytes,
      macKeyBytes = macKeyBytes,
      senderIdPubKey = senderIdKeyPair.publicKey,
      receiverIdPubKey = receiverIdPubKey
    )
    return EncryptionOutput(
      iv = encode(ivBytes),
      mac = encode(mac),
      ciphertext = encode(cipherBytes),
      senderEphPubKey = senderEphKeyPair?.publicKey
    )
  }

  fun decrypt(input: DecryptionInput) : String {
    val ss1 = calculateAgreement(input.senderIdPubKey, input.receiverIdKeyPair.privateKey)
    val stream = ByteArrayOutputStream()
    stream.write(ss1)
    if (input.senderEphPubKey != null && input.receiverEphPriKey != null) {
      val ss2 = calculateAgreement(input.senderEphPubKey, input.receiverEphPriKey)
      stream.write(ss2)
    }
    val master = stream.toByteArray()
    val salt = ByteArray(32)
    val info = "Cryptchat".toByteArray()
    val prk = extract(salt, master)
    val derived = expand(prk, info, 64)

    val macKeyBytes = derived.copyOfRange(32, 64)
    val cipherBytes = decode(input.ciphertext)
    val ivBytes = decode(input.iv)
    val ourMac = getMac(
      cipherBytes = cipherBytes + ivBytes,
      macKeyBytes = macKeyBytes,
      senderIdPubKey = input.senderIdPubKey,
      receiverIdPubKey = input.receiverIdKeyPair.publicKey
    )
    val theirMac = decode(input.mac)
    if (!MessageDigest.isEqual(ourMac, theirMac)) {
      throw BadMac("BAD MAC")
    }

    val cipherKeyBytes = derived.copyOfRange(0, 32)
    val plaintextBytes = toPlaintextBytes(
      ciphertextBytes = cipherBytes,
      ivBytes = ivBytes,
      cipherKeyBytes = cipherKeyBytes
    )
    return String(plaintextBytes)
  }

  private fun encode(bytes: ByteArray) : String {
    return Base64.encodeToString(bytes, Base64.DEFAULT)
  }

  private fun decode(string: String) : ByteArray {
    return Base64.decode(string, Base64.DEFAULT)
  }

  private fun calculateAgreement(pubKey: ECPublicKey, priKey: ECPrivateKey) : ByteArray {
    return Curve25519.getInstance(Curve25519.BEST).calculateAgreement(
      pubKey.toByteArray(),
      priKey.toByteArray()
    )
  }

  private fun getMac(
    cipherBytes: ByteArray,
    macKeyBytes: ByteArray,
    senderIdPubKey: ECPublicKey,
    receiverIdPubKey: ECPublicKey
  ) : ByteArray {
    val macKey = SecretKeySpec(macKeyBytes, "HmacSHA256")
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(macKey)
    mac.update(senderIdPubKey.toByteArray())
    mac.update(receiverIdPubKey.toByteArray())
    return mac.doFinal(cipherBytes).copyOfRange(0, 16)
  }

  private fun toCiphertextBytes(
    plaintextBytes: ByteArray,
    ivBytes: ByteArray,
    cipherKeyBytes: ByteArray
  ) : ByteArray {
    val iv = IvParameterSpec(ivBytes)
    val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
    val cipherKey = SecretKeySpec(cipherKeyBytes, "AES")
    cipher.init(Cipher.ENCRYPT_MODE, cipherKey, iv)
    return cipher.doFinal(plaintextBytes)
  }

  private fun toPlaintextBytes(
    ciphertextBytes: ByteArray,
    ivBytes: ByteArray,
    cipherKeyBytes: ByteArray
  ) : ByteArray {
    val iv = IvParameterSpec(ivBytes)
    val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
    val cipherKey = SecretKeySpec(cipherKeyBytes, "AES")
    cipher.init(Cipher.DECRYPT_MODE, cipherKey, iv)
    return cipher.doFinal(ciphertextBytes)
  }

  private fun extract(salt: ByteArray, input: ByteArray): ByteArray {
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(SecretKeySpec(salt, "HmacSHA256"))
    return mac.doFinal(input)
  }

  private fun expand(prk: ByteArray, info: ByteArray?, size: Int): ByteArray {
    val iterations =
      ceil(size.toDouble() / 32.0).toInt()
    var mixin: ByteArray? = ByteArray(0)
    val results = ByteArrayOutputStream()
    var remainingBytes: Int = size

    for (i in 1 until iterations + 1) {
      val mac = Mac.getInstance("HmacSHA256")
      mac.init(SecretKeySpec(prk, "HmacSHA256"))
      mac.update(mixin)
      if (info != null) {
        mac.update(info)
      }
      mac.update(i.toByte())
      val stepResult = mac.doFinal()
      val stepSize = remainingBytes.coerceAtMost(stepResult.size)
      results.write(stepResult, 0, stepSize)
      mixin = stepResult
      remainingBytes -= stepSize
    }

    return results.toByteArray()
  }
}