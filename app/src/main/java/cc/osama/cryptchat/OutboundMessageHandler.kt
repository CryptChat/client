package cc.osama.cryptchat

import android.content.Context
import android.util.Log.e
import android.util.Log.w
import cc.osama.cryptchat.db.Message
import cc.osama.cryptchat.db.Server
import cc.osama.cryptchat.db.User
import cc.osama.cryptchat.ui.ChatView
import com.android.volley.VolleyError
import org.json.JSONObject
import java.lang.Exception
import java.lang.IllegalArgumentException

class OutboundMessageHandler(
  private var message: Message,
  private val user: User,
  private val server: Server,
  private val context: Context
) {
  constructor(
    plaintext: String,
    user: User,
    server: Server,
    context: Context
  ) : this(
    context = context,
    message = Message(
      serverId = server.id,
      userId = user.id,
      status = Message.INITIAL_STATE,
      plaintext = plaintext,
      createdAt = System.currentTimeMillis(),
      read = true,
      idOnServer = null
    ),
    server = server,
    user = user
  )

  fun process() {
    val stringKey = message.receiverEphemeralPublicKey
    val idOnReceiverDevice = message.receiverEphemeralKeyPairId
    if (stringKey != null && idOnReceiverDevice != null) {
      val ephPubKey = ECPublicKey.EphPubKeyFromServer(
        stringKey = stringKey,
        idOnUserDevice = idOnReceiverDevice
      )
      encryptAndSend(ephPubKey)
    } else {
      fetchReceiverEphemeralPublicKey { ephPubKey, serverError ->
        if (serverError == null) {
          message.receiverEphemeralKeyPairId = ephPubKey?.idOnUserDevice
          message.receiverEphemeralPublicKey = ephPubKey?.key?.toString()
          encryptAndSend(ephPubKey)
        } else {
          message.status = Message.NEEDS_RETRY
          updateMessageInDb()
        }
      }
    }
  }

  fun saveToDb(callback: () -> Unit) {
    Cryptchat.db(context).also { db ->
      AsyncExec.run {
        val message = db.messages().add(this.message)
        this.message = message
        it.execMainThread(callback)
        ChatView.notifyNewMessage(context)
      }
    }
  }

  private fun encryptAndSend(ephPubKey: ECPublicKey.EphPubKeyFromServer?) {
    var encryptionOutput: CryptchatSecurity.EncryptionOutput? = null
    try {
      encryptionOutput = encrypt(ephPubKey)
    } catch (ex: Exception) {
      e("ENCRYPTION", "ENCRYPTION FAILED", ex)
      message.status = Message.ENCRYPTION_FAILED
      updateMessageInDb()
    }
    if (encryptionOutput != null) send(encryptionOutput)
  }

  private fun updateMessageInDb(callback: (() -> Unit)? = null) {
    Cryptchat.db(context).also { db ->
      AsyncExec.run {
        db.messages().update(message)
        if (callback != null) it.execMainThread(callback)
        ChatView.notifyModifiedMessage(message.id, context)
      }
    }
  }

  private fun send(encryptionOutput: CryptchatSecurity.EncryptionOutput) {
    val param = JSONObject().also { param ->
      param.put("message", JSONObject().also { msg ->
        msg.put("body", encryptionOutput.ciphertext)
        msg.put("mac", encryptionOutput.mac)
        msg.put("iv", encryptionOutput.iv)
        msg.put("receiver_user_id", user.idOnServer)
        msg.put("sender_ephemeral_public_key", encryptionOutput.senderEphPubKey?.toString())
        msg.put("ephemeral_key_id_on_user_device", message.receiverEphemeralKeyPairId)
      })
    }
    CryptchatServer(context, server).post(
      path = "/message.json",
      param = param,
      success = { json ->
        val idOnServer = CryptchatUtils.toLong((json["message"] as? JSONObject)?.get("id"))
        AsyncExec.run {
          message.status = Message.SENT
          message.idOnServer = idOnServer
          updateMessageInDb()
        }
      }, failure = {
        message.status = Message.NEEDS_RETRY
        updateMessageInDb()
      }
    )
  }

  private fun encrypt(ephPubKey: ECPublicKey.EphPubKeyFromServer?) : CryptchatSecurity.EncryptionOutput {
    return CryptchatSecurity().encrypt(
      message = message.plaintext,
      senderIdKeyPair = server.keyPair,
      receiverIdPubKey = user.publicKey,
      receiverEphPubKey = ephPubKey?.key
    )
  }

  private fun fetchReceiverEphemeralPublicKey(
    callback: (
      ECPublicKey.EphPubKeyFromServer?,
      CryptchatServer.CryptchatServerError?
    ) -> Unit
  ) {
    CryptchatServer(context, server).post(
      path = "/ephemeral-keys/grab.json",
      param = JSONObject().also { it.put("user_id", user.idOnServer) },
      success = {
        val ephemeralPublicKey = extractEphKeyFromJson(it)
        callback(ephemeralPublicKey, null)
      },
      failure = {
        if (it.statusCode == 404) {
          message.status = Message.RECEIVER_DELETED
          updateMessageInDb()
          return@post
        }
        callback(null, it)
      }
    )
  }

  private fun extractEphKeyFromJson(json: JSONObject) : ECPublicKey.EphPubKeyFromServer? {
    val keyJson = json["ephemeral_key"] as? JSONObject ?: return null
    val stringKey = keyJson["key"] as? String
    val idOnUserDevice = CryptchatUtils.toLong(keyJson["id_on_user_device"])
    return if (stringKey != null && idOnUserDevice != null) {
      try {
        ECPublicKey.EphPubKeyFromServer(
          stringKey = stringKey,
          idOnUserDevice = idOnUserDevice
        )
      } catch (ex: IllegalArgumentException) {
        null
      }
    } else {
      null
    }
  }
}