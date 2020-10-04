package cc.osama.cryptchat.worker

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.util.Log
import android.util.Log.e
import android.util.Log.w
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.*
import cc.osama.cryptchat.*
import cc.osama.cryptchat.R
import cc.osama.cryptchat.db.Message
import cc.osama.cryptchat.ui.ServerUsersList
import cc.osama.cryptchat.ui.ServersList
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom

class SyncMessagesWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
  companion object {
    fun enqueue(serverId: Long, context: Context) {
      val workerArgs = Data.Builder().also { data ->
        data.putLong("serverId", serverId)
      }.build()
      val syncMessagesRequest = OneTimeWorkRequestBuilder<SyncMessagesWorker>()
        .setInputData(workerArgs)
        .build()
      WorkManager.getInstance(context).enqueue(syncMessagesRequest)
    }
  }
  override fun doWork() : Result {
    val serverId = inputData.getLong("serverId", -1)
    val db = Cryptchat.db(applicationContext)
    val server = db.servers().findById(serverId) ?: return Result.success()
    val lastSeenId = db.messages().findNewestReceivedMessageFromServer(server.id) ?: 0
    val param = JSONObject()
    param.put("last_seen_id", lastSeenId)
    CryptchatServer(applicationContext, server).request(
      method = CryptchatRequest.Methods.POST,
      path = "/sync/messages.json",
      param = param,
      async = false,
      success = {
        val messages = it.optJSONArray("messages") ?: return@request
        try {
          for (m in 0 until messages.length()) {
            val messageJson = messages[m] as? JSONObject ?: continue
            val handler = InboundMessageHandler(
              data = messageJson,
              server = server,
              context = applicationContext
            )
            val message = handler.process() ?: return@request
            val user = db.users().find(message.userId) ?: return@request
            if (message.decrypted()) {
              ServerUsersList.refreshUsersList(applicationContext)
              ServersList.refreshList(applicationContext)
              NotificationCompat.Builder(applicationContext, Cryptchat.MESSAGES_CHANNEL_ID).also { builder ->
                builder.setContentText(message.plaintext)
                builder.setContentTitle(
                  applicationContext.resources.getString(
                R.string.message_notification_title, user.displayName(), server.displayName()
                )
                )
                builder.priority = NotificationCompat.PRIORITY_DEFAULT
                // TODO: Replace this with a proper icon
                builder.setSmallIcon(R.drawable.ic_check_black_24dp)
                with(NotificationManagerCompat.from(applicationContext)) {
                  notify(SecureRandom().nextInt(), builder.build())
                }
              }
            }
          }
        } catch (ex: InboundMessageHandler.UserNotFound) {
          if (!Cryptchat.isReadonly(applicationContext)) {
            SyncUsersWorker.enqueue(
              serverId = server.id,
              scheduleMessagesSync = true,
              context = applicationContext
            )
          }
        }
      },
      failure = {
        e("MESSAGE SYNC", "MESSAGES SYNC API POINT FAILURE $it")
      }
    )
    return Result.success()
  }
}