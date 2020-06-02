package cc.osama.cryptchat

import android.util.Log.d
import android.util.Log.w
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import cc.osama.cryptchat.worker.SyncMessagesWorker
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import org.json.JSONObject

class CryptchatFirebaseMessagingService : FirebaseMessagingService() {
  override fun onMessageReceived(message: RemoteMessage) {
    super.onMessageReceived(message)
    d("TOKEN", "MESSAGE RECEIVED ${message.from} dsadasd")
    val command = message.data["command"] ?: return
    val from = message.from ?: return
    val server = Cryptchat.db(applicationContext).servers().findBySenderId(from) ?: return
    if (command == "sync_messages") {
      val workerArgs = Data.Builder().also { data ->
        data.putLong("serverId", server.id)
      }.build()
      val syncMessagesRequest = OneTimeWorkRequestBuilder<SyncMessagesWorker>()
        .setInputData(workerArgs)
        .build()
      WorkManager.getInstance(applicationContext).enqueue(syncMessagesRequest)
    }

    /* if (msg != null) {
      Cryptchat?.currentChatView?.addToMessagesAndNotify(msg)
    } */
  }
}