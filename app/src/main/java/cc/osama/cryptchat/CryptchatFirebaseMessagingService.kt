package cc.osama.cryptchat

import android.util.Log.d
import cc.osama.cryptchat.worker.SyncMessagesWorker
import cc.osama.cryptchat.worker.SyncUsersWorker
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class CryptchatFirebaseMessagingService : FirebaseMessagingService() {
  override fun onMessageReceived(message: RemoteMessage) {
    super.onMessageReceived(message)
    d("TOKEN", "MESSAGE RECEIVED ${message.from} dsadasd")
    val command = message.data["command"] ?: return
    val from = message.from ?: return
    val server = Cryptchat.db(applicationContext).servers().findBySenderId(from) ?: return
    if (command == "sync_messages") {
      SyncMessagesWorker.enqueue(
        serverId = server.id,
        context = applicationContext
      )
    } else if (command == "sync_users") {
      SyncUsersWorker.enqueue(
        serverId = server.id,
        context = applicationContext
      )
    }
  }
}