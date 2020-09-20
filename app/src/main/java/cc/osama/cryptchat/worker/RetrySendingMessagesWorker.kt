package cc.osama.cryptchat.worker

import android.content.Context
import androidx.work.*
import cc.osama.cryptchat.Cryptchat
import cc.osama.cryptchat.OutboundMessageHandler

class RetrySendingMessagesWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
  companion object {
    fun enqueue(context: Context) {
      val request = OneTimeWorkRequestBuilder<RetrySendingMessagesWorker>().apply {
        setConstraints(
          Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        )
      }.build()
      WorkManager.getInstance(context).enqueue(request)
    }
  }

  override fun doWork() : Result {
    val db = Cryptchat.db(applicationContext)
    db.messages().unsentMessages().forEach { msg ->
      val user = db.users().find(msg.userId)
      val server = db.servers().findById(msg.serverId)
      if (user == null || server == null) return@forEach
      OutboundMessageHandler(msg, user, server, applicationContext).process()
    }
    return Result.success()
  }
}