package cc.osama.cryptchat.worker

import android.content.Context
import android.util.Log.d
import android.util.Log.w
import androidx.work.Worker
import androidx.work.WorkerParameters
import cc.osama.cryptchat.Cryptchat
import cc.osama.cryptchat.CryptchatServer
import cc.osama.cryptchat.CryptchatUtils
import org.json.JSONArray
import org.json.JSONObject

class SyncMessagesWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
  override fun doWork() : Result {
    val serverId = inputData.getLong("serverId", -1)
    val db = Cryptchat.db(applicationContext)
    val server = db.servers().findById(serverId) ?: return Result.success()
    val lastSeenId = db.messages().findNewestReceivedMessageFromServer(server.id) ?: 0
    CryptchatServer(applicationContext, server.address).get(
      path = "/sync/messages.json",
      param = JSONObject().also { json -> json.put("last_seen_id", lastSeenId); json.put("user_id", server.userId) },
      success = {
        val messages = it["messages"] as? JSONArray ?: return@get
        for (m in 0 until messages.length()) {
          val messageJson = messages[m] as? JSONObject ?: continue
          val idOnServer = CryptchatUtils.toLong(messageJson["id"]) ?: continue
          val body = messageJson["body"] as? String ?: continue
          val iv = messageJson["iv"] as? String ?: continue
          val mac = messageJson["mac"] as? String ?: continue
          val senderIdOnServer = CryptchatUtils.toLong(messageJson["sender_user_id"]) ?: continue
          val createdAt = CryptchatUtils.toLong(messageJson["created_at"]) ?: continue
          val senderEphPubKey = messageJson["sender_ephemeral_public_key"] as? String
          val ephemeralKeyId = CryptchatUtils.toLong(messageJson["ephemeral_key_id_on_user_device"])
          if ((senderEphPubKey == null && ephemeralKeyId != null) ||
            (senderEphPubKey != null && ephemeralKeyId == null)) {
            d("DECRYPTION WEIRDNESS", "sender ephemeral public key is present but not receiver ephemeral keypair id (or vice versa)")
            continue
          }
        }
      },
      failure = {
        w("USERRRRR w", it.toString())
      }
    )
    return Result.success()
  }
}