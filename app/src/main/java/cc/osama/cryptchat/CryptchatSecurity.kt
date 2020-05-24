package cc.osama.cryptchat

import android.util.Log
import org.whispersystems.curve25519.Curve25519
import org.whispersystems.curve25519.Curve25519KeyPair
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.math.ceil

class CryptchatSecurity {
  companion object {
    fun genKeyPair(): ECKeyPair {
      val keyPair = Curve25519.getInstance(Curve25519.BEST).generateKeyPair()
      val public = ECPublicKey(keyPair.publicKey)
      val private = ECPrivateKey(keyPair.privateKey)
      return ECKeyPair(public, private)
    }
  }
  fun encrypt(
    message: String,
    senderIdPriKey: ByteArray,
    senderEphPriKey: ByteArray,
    receiverIdPubKey: ByteArray,
    receiverEphPubKey: ByteArray,
    senderIdPubKey: ByteArray
  ): ArrayList<ByteArray> {
    val ss1 = Curve25519.getInstance(Curve25519.BEST).calculateAgreement(receiverIdPubKey, senderIdPriKey)
    val ss2 = Curve25519.getInstance(Curve25519.BEST).calculateAgreement(receiverEphPubKey, senderEphPriKey)
    val stream = ByteArrayOutputStream()
    stream.write(ss1)
    stream.write(ss2)
    val master = stream.toByteArray()
    val salt = ByteArray(32)
    val info = "Cryptchat".toByteArray()
    val prk = extract(salt, master)
    val derived = expand(prk, info, 80)
    val cipherKey = SecretKeySpec(derived.copyOfRange(0, 32), "AES")
    val macKey = SecretKeySpec(derived.copyOfRange(32, 32 + 32), "HmacSHA256")
    val iv = IvParameterSpec(derived.copyOfRange(32 + 32, 32 + 32 + 16))
    val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
    cipher.init(Cipher.ENCRYPT_MODE, cipherKey, iv)
    val cipherBytes = cipher.doFinal(message.toByteArray())
    val mac = getMac(cipherBytes, macKey, senderIdPubKey, receiverIdPubKey)
    val list = ArrayList<ByteArray>(2)
    list.add(mac)
    list.add(cipherBytes)
    return list
  }

  fun decrypt(
    cipherList: ArrayList<ByteArray>,
    senderIdPubKey: ByteArray,
    senderEphPubKey: ByteArray,
    receiverIdPriKey: ByteArray,
    receiverEphPriKey: ByteArray,
    receiverIdPubKey: ByteArray
  ): String {
    val theirMac = cipherList.get(0)
    val cipherBytes = cipherList.get(1)
    val ss1 = Curve25519.getInstance(Curve25519.BEST).calculateAgreement(senderIdPubKey, receiverIdPriKey)
    val ss2 = Curve25519.getInstance(Curve25519.BEST).calculateAgreement(senderEphPubKey, receiverEphPriKey)
    val stream = ByteArrayOutputStream()
    stream.write(ss1)
    stream.write(ss2)
    val master = stream.toByteArray()
    val salt = ByteArray(32)
    val info = "Cryptchat".toByteArray()
    val prk = extract(salt, master)
    val derived = expand(prk, info, 80)
    val cipherKey = SecretKeySpec(derived.copyOfRange(0, 32), "AES")
    val macKey = SecretKeySpec(derived.copyOfRange(32, 32 + 32), "HmacSHA256")
    val iv = IvParameterSpec(derived.copyOfRange(32 + 32, 32 + 32 + 16))
    val ourMac = getMac(cipherBytes, macKey, senderIdPubKey, receiverIdPubKey)
    Log.w("INTEGRITY", (MessageDigest.isEqual(ourMac, theirMac)).toString())
    val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
    cipher.init(Cipher.DECRYPT_MODE, cipherKey, iv)
    val plainBytes = cipher.doFinal(cipherBytes)
    return String(plainBytes)
  }

  fun getMac(msg: ByteArray, macKey: SecretKeySpec, senderIdPubKey: ByteArray, receiverIdPubKey: ByteArray): ByteArray {
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(macKey)
    mac.update(senderIdPubKey)
    mac.update(receiverIdPubKey)
    val final = mac.doFinal(msg).copyOfRange(0, 8)
    return final
  }

  fun extract(salt: ByteArray, input: ByteArray): ByteArray {
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(SecretKeySpec(salt, "HmacSHA256"))
    return mac.doFinal(input)
  }

  fun expand(prk: ByteArray, info: ByteArray?, size: Int): ByteArray {
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
      val stepSize = Math.min(remainingBytes, stepResult.size)
      results.write(stepResult, 0, stepSize)
      mixin = stepResult
      remainingBytes -= stepSize
    }

    return results.toByteArray()
  }
}