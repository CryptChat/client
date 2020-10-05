package cc.osama.cryptchat.worker

import android.content.Context
import android.util.Log.e
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.edit
import androidx.work.*
import cc.osama.cryptchat.*
import cc.osama.cryptchat.R
import cc.osama.cryptchat.ui.ServerUsersList
import cc.osama.cryptchat.ui.ServersList
import org.json.JSONObject
import java.security.SecureRandom
import java.util.concurrent.TimeUnit

class SyncMessagesWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
  companion object {
    fun enqueue(serverId: Long, secondsDelay: Int? = null, context: Context) {
      val workerArgs = Data.Builder().also { data ->
        data.putLong("serverId", serverId)
      }.build()
      val syncMessagesRequest = OneTimeWorkRequestBuilder<SyncMessagesWorker>().also {
        it.setInputData(workerArgs)
        if (secondsDelay != null) {
          it.setInitialDelay(secondsDelay.toLong(), TimeUnit.SECONDS)
        }
      }.build()
      WorkManager.getInstance(context).enqueue(syncMessagesRequest)
    }
  }

  override fun doWork() : Result {
    val serverId = inputData.getLong("serverId", -1)
    val db = Cryptchat.db(applicationContext)
    val server = db.servers().findById(serverId) ?: return Result.success()
    val preferences = Cryptchat.sharedPreferences(applicationContext)
    val key = "sync_messages_worker_lock_${server.id}"
    try {
      synchronized(SyncMessagesWorker) {
        val locked = preferences.getBoolean(key, false)
        if (locked) {
          enqueue(server.id, 5, applicationContext)
          return Result.success()
        } else {
          preferences.edit {
            putBoolean(key, true)
            commit()
          }
        }
      }
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
          e("SyncMessagesWorker", "MESSAGES SYNC API POINT FAILURE ${it}")
        }
      )
    } finally {
      synchronized(SyncMessagesWorker) {
        preferences.edit {
          remove(key)
          commit()
        }
      }
    }
    return Result.success()
  }
}