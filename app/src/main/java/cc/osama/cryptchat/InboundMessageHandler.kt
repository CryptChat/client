package cc.osama.cryptchat

import android.content.Context
import android.content.Intent
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import cc.osama.cryptchat.db.EphemeralKey
import cc.osama.cryptchat.db.Message
import cc.osama.cryptchat.db.Server
import cc.osama.cryptchat.ui.ServerUsersList
import org.json.JSONObject
import java.lang.Exception

class InboundMessageHandler(
  private val data: JSONObject,
  private val server: Server,
  private val context: Context
) {
  class UserNotFound() : Exception()

  fun process() {
    val db = Cryptchat.db(context)
    val messageIdOnServer = CryptchatUtils.toLong(data["id"]) ?: return
    if (db.messages().checkMessageExists(serverId = server.id, idOnServer = messageIdOnServer)) {
      return
    }
    val body = data["body"] as? String ?: return
    val iv = data["iv"] as? String ?: return
    val mac = data["mac"] as? String ?: return
    val senderIdOnServer = CryptchatUtils.toLong(data["sender_user_id"]) ?: return
    val createdAt = CryptchatUtils.toLong(data["created_at"]) ?: return
    val senderEphPubKeyString = data["sender_ephemeral_public_key"] as? String
    val receiverEphKeyPairId = CryptchatUtils.toLong(data["ephemeral_key_id_on_user_device"])
    val senderUser = db.users().findUserByServerIdAndIdOnServer(serverId = server.id, idOnServer = senderIdOnServer)
      ?: throw UserNotFound() // TODO: add safety guards to ensure we remember not found IDs and not fail for them again

    var status = Message.DECRYPTED
    var plaintext = ""
    var receiverEphKeyPair: EphemeralKey? = null
    var attemptDecryption = true
    try {
      if ((senderEphPubKeyString == null && receiverEphKeyPairId != null) ||
        (senderEphPubKeyString != null && receiverEphKeyPairId == null)) {
        // if one is present and the other is null
        status = Message.INCONSISTENT_STATE
        attemptDecryption = false
      }
      if (receiverEphKeyPairId != null) {
        receiverEphKeyPair = db.ephemeralKeys().findById(receiverEphKeyPairId)
        if (receiverEphKeyPair == null) {
          status = Message.DELETED_EPHEMERAL_KEYPAIR
          attemptDecryption = false
        }
      }
      var senderEphPubKey: ECPublicKey? = null
      if (senderEphPubKeyString != null && attemptDecryption) {
        senderEphPubKey = ECPublicKey(senderEphPubKeyString)
      }
      if (attemptDecryption) {
        val decryptionInput = CryptchatSecurity.DecryptionInput(
          iv = iv,
          mac = mac,
          ciphertext = body,
          senderIdPubKey = senderUser.publicKey,
          senderEphPubKey = senderEphPubKey,
          receiverIdKeyPair = server.keyPair,
          receiverEphPriKey = receiverEphKeyPair?.keyPair?.privateKey
        )
        plaintext = CryptchatSecurity().decrypt(decryptionInput)
      }
    } catch (ex: CryptchatSecurity.BadMac) {
      status = Message.BAD_MAC
      plaintext = "BAD MAC"
    } catch (ex: Exception) {
      plaintext = ex.javaClass.name + ex.stackTrace.joinToString(",\n") { "${it.className} # ${it.fileName} # ${it.methodName} + ${it.lineNumber}" }
      status = Message.DECRYPTION_FAILED
    }
    db.messages().add(Message(
      plaintext = plaintext,
      userId = senderUser.id,
      idOnServer = messageIdOnServer,
      serverId = server.id,
      createdAt = createdAt,
      read = false,
      status = status,
      senderPublicEphemeralKey = senderEphPubKeyString,
      receiverEphemeralKeyPairId = receiverEphKeyPair?.id
    ))
    LocalBroadcastManager.getInstance(context).also { broadcast ->
      val intent = Intent(ServerUsersList.REFRESH_COMMAND)
      intent.putExtra("command", ServerUsersList.REFRESH_COMMAND)
      broadcast.sendBroadcast(intent)
    }
  }
}