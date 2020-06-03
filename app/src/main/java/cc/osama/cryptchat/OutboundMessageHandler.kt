package cc.osama.cryptchat

import android.content.Context
import android.util.Log.w
import cc.osama.cryptchat.db.Message
import cc.osama.cryptchat.db.Server
import cc.osama.cryptchat.db.User
import org.json.JSONObject

class OutboundMessageHandler(
  plaintext: String,
  private val user: User,
  private val server: Server,
  private val context: Context
) {
  private var message: Message = Message(
    serverId = server.id,
    userId = user.id,
    status = Message.PENDING,
    plaintext = plaintext,
    createdAt = System.currentTimeMillis(),
    read = true,
    idOnServer = null
  )

  private class EphPubKeyFromServer(
    private val stringKey: String,
    val key: ECPublicKey = ECPublicKey(stringKey),
    val idOnServer: Long,
    val idOnUserDevice: Long
  )

  fun saveToDb(callback: (Message) -> Unit) {
    Cryptchat.db(context).also { db ->
      AsyncExec.run {
        val message = db.messages().add(this.message)
        this.message = message
        it.execMainThread {
          callback(message)
        }
      }
    }
  }

  fun encryptAndSend() {
    val message = this.message ?: return
    fetchReceiverEphemeralPublicKey() {
      val encryptionOutput = encrypt(ephPubKey = it)
      message.receiverEphemeralKeyPairId = it?.idOnUserDevice
      message.senderPublicEphemeralKey = encryptionOutput.senderEphPubKey?.toString()
      Cryptchat.db(context).also { db ->
        AsyncExec.run {
          db.messages().update(message)
          send(message, encryptionOutput)
        }
      }
    }
  }

  private fun send(message: Message, encryptionOutput: CryptchatSecurity.EncryptionOutput) {
    val param = JSONObject().also { param ->
      param.put("message", JSONObject().also { msg ->
        msg.put("body", encryptionOutput.ciphertext)
        msg.put("mac", encryptionOutput.mac)
        msg.put("iv", encryptionOutput.iv)
        msg.put("receiver_user_id", user.idOnServer)
        msg.put("sender_user_id", server.userId) // TODO: Remove when auth is implemented
        msg.put("sender_ephemeral_public_key", encryptionOutput.senderEphPubKey?.toString())
        msg.put("ephemeral_key_id_on_user_device", message.receiverEphemeralKeyPairId)
      })
    }
    CryptchatServer(context, server.address).post(
      path = "/message.json",
      param = param,
      success = { json ->
        val idOnServer = CryptchatUtils.toLong((json["message"] as? JSONObject)?.get("id"))
        Cryptchat.db(context).also { db ->
          AsyncExec.run {
            message.status = Message.SENT
            message.idOnServer = idOnServer
            db.messages().update(message)
          }
        }
      }
    )
  }

  private fun encrypt(ephPubKey: EphPubKeyFromServer?) : CryptchatSecurity.EncryptionOutput {
    return CryptchatSecurity().encrypt(
      message = message.plaintext,
      senderIdKeyPair = server.keyPair,
      receiverIdPubKey = user.publicKey,
      receiverEphPubKey = ephPubKey?.key
    )
  }

  private fun fetchReceiverEphemeralPublicKey(callback: (EphPubKeyFromServer?) -> Unit) {
    CryptchatServer(context, server.address).post(
      path = "/ephemeral-keys/grab.json",
      param = JSONObject().also { it.put("user_id", user.idOnServer) },
      success = {
        val ephemeralPublicKey = extractEphKeyFromJson(it)
        callback(ephemeralPublicKey)
      }
    )
  }

  private fun extractEphKeyFromJson(json: JSONObject) : EphPubKeyFromServer? {
    val keyJson = json["ephemeral_key"] as? JSONObject ?: return null
    val stringKey = keyJson["key"] as? String
    val idOnUserDevice = CryptchatUtils.toLong(keyJson["id_on_user_device"])
    val idOnServer = CryptchatUtils.toLong(keyJson["id"])
    return if (stringKey != null && idOnUserDevice != null && idOnServer != null) {
      EphPubKeyFromServer(
        stringKey = stringKey,
        idOnUserDevice = idOnUserDevice,
        idOnServer = idOnServer
      )
    } else {
      null
    }
  }
}