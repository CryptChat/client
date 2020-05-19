package cc.osama.cryptchat

import android.util.Log.d
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import org.json.JSONObject

class CryptchatFirebaseMessagingService : FirebaseMessagingService() {
  override fun onNewToken(token: String) {
    super.onNewToken(token)
    val param = JSONObject()
    val user = JSONObject()
    user.put("instance_id", token)
    param.put("user", user)
    // SechatServer.put("/user/1.json", param, this.applicationContext, { data: JSONObject ->
    //   d("TOKEN", "SUCCESS!")
    // }, { error ->
    //   d("TOKEN", "FAILURE!")
    // })
  }

  override fun onMessageReceived(p0: RemoteMessage) {
    super.onMessageReceived(p0)
    val msg = p0.data["message"]
    if (msg != null) {
      Cryptchat?.currentChatView?.addToMessagesAndNotify(msg)
    }
    // d("TOKEN", p0.data.size.toString())
    // for (j in p0.data.keys) {
    //   d("TOKEN", p0.data[j])
    // }
    d("TOKEN", "MESSAGE RECEIVED")
  }
}