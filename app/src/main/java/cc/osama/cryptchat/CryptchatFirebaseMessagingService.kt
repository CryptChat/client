package cc.osama.cryptchat

import android.util.Log.d
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import org.json.JSONObject

class CryptchatFirebaseMessagingService : FirebaseMessagingService() {
  override fun onMessageReceived(message: RemoteMessage) {
    super.onMessageReceived(message)
    val senderUserId = CryptchatUtils.toLong(message.data["sender_id"]) ?: return
    val from = message.from ?: return
    val server = Cryptchat.db(applicationContext).servers().findBySenderId(from) ?: return

    /* if (msg != null) {
      Cryptchat?.currentChatView?.addToMessagesAndNotify(msg)
    } */
    d("TOKEN", "MESSAGE RECEIVED ${message.from} dsadasd")
  }
}