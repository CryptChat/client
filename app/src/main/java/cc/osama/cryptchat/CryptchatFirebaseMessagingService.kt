package cc.osama.cryptchat

import android.util.Log.d
import cc.osama.cryptchat.worker.InstanceIdsManagerWorker
import cc.osama.cryptchat.worker.SyncMessagesWorker
import cc.osama.cryptchat.worker.SyncUsersWorker
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class CryptchatFirebaseMessagingService : FirebaseMessagingService() {
  override fun onMessageReceived(message: RemoteMessage) {
    super.onMessageReceived(message)
    if (Cryptchat.isReadonly(applicationContext)) {
      d("FirebaseMessagingSrv", "returning from onMessageReceived early cuz readonly mode is on.")
      return
    }
    d("FirebaseMessagingSrv", "received firebase message. data=${message.data}")
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

  override fun onNewToken(p0: String) {
    super.onNewToken(p0)
    if (Cryptchat.isReadonly(applicationContext)) {
      d("FirebaseMessagingSrv", "returning from onNewToken early cuz readonly mode is on.")
      return
    }
    InstanceIdsManagerWorker.enqueue(applicationContext)
  }
}