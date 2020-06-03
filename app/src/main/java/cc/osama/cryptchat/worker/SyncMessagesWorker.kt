package cc.osama.cryptchat.worker

import android.content.Context
import android.util.Log
import android.util.Log.w
import androidx.work.*
import cc.osama.cryptchat.*
import cc.osama.cryptchat.db.Message
import org.json.JSONArray
import org.json.JSONObject

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
    AsyncExec.run {
      val db = Cryptchat.db(applicationContext)
      val server = db.servers().findById(serverId) ?: return@run
      val lastSeenId = db.messages().findNewestReceivedMessageFromServer(server.id) ?: 0
      val param = JSONObject()
      param.put("last_seen_id", lastSeenId)
      param.put("user_id", server.userId)
      CryptchatServer(applicationContext, server.address).post(
        path = "/sync/messages.json",
        param = param,
        success = {
          val messages = it["messages"] as? JSONArray ?: return@post
          AsyncExec.run {
            try {
              for (m in 0 until messages.length()) {
                val messageJson = messages[m] as? JSONObject ?: continue
                val handler = InboundMessageHandler(
                  data = messageJson,
                  server = server,
                  context = applicationContext
                )
                handler.process()
              }
            } catch (ex: InboundMessageHandler.UserNotFound) {
              SyncUsersWorker.enqueue(
                serverId = server.id,
                scheduleMessagesSync = true,
                context = applicationContext
              )
            }
          }
        },
        failure = {
          Log.d("TOKEN", "MESSAGES SYNC HIT API POINT FAILURE")
        }
      )
    }
    return Result.success()
  }
}