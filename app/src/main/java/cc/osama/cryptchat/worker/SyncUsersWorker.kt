package cc.osama.cryptchat.worker

import android.content.Context
import androidx.work.*
import cc.osama.cryptchat.*
import cc.osama.cryptchat.db.User
import org.json.JSONArray
import org.json.JSONObject

class SyncUsersWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
  companion object {
    fun enqueue(serverId: Long, scheduleMessagesSync: Boolean = false, context: Context) {
      val workerArgs = Data.Builder().also { data ->
        data.putLong("serverId", serverId)
        data.putBoolean("scheduleMessagesSync", scheduleMessagesSync)
      }.build()
      val syncMessagesRequest = OneTimeWorkRequestBuilder<SyncUsersWorker>()
        .setInputData(workerArgs)
        .build()
      WorkManager.getInstance(context).enqueue(syncMessagesRequest)
    }
  }

  override fun doWork() : Result {
    val serverId = inputData.getLong("serverId", -1)
    val scheduleMessagesSync = inputData.getBoolean("scheduleMessagesSync", false)
    val db = Cryptchat.db(applicationContext)
    val server = db.servers().findById(serverId) ?: return Result.success()

    CryptchatServer(applicationContext, server).post(
      path = "/sync/users.json",
      success = {
        val usersJsonArray = it["users"] as? JSONArray ?: return@post
        val users = mutableListOf<User>()
        AsyncExec.run {
          for (i in 0 until usersJsonArray.length()) {
            val userJson = usersJsonArray[i] as? JSONObject ?: continue
            val publicKey = if (userJson["identity_key"] as? String != null) ECPublicKey(userJson["identity_key"] as String) else null
            val countryCode = userJson["country_code"] as? String
            val phoneNumber = userJson["phone_number"] as? String
            val idOnServer = CryptchatUtils.toLong(userJson["id"])
            val lastUpdatedAt = CryptchatUtils.toLong(userJson["updated_at"])
            val name = userJson["name"] as? String
            if (publicKey != null &&
              countryCode != null &&
              phoneNumber != null &&
              idOnServer != null &&
              idOnServer != server.userId &&
              lastUpdatedAt != null &&
              db.users().findUserByServerIdAndIdOnServer(serverId = server.id, idOnServer = idOnServer) == null
            ) {
              users.add(
                User(
                  serverId = server.id,
                  publicKey = publicKey,
                  lastUpdatedAt = lastUpdatedAt,
                  phoneNumber = phoneNumber,
                  countryCode = countryCode,
                  idOnServer = idOnServer,
                  name = name
                )
              )
            }
          }
          db.users().addMany(users)
          if (scheduleMessagesSync) {
            SyncMessagesWorker.enqueue(
              serverId = server.id,
              context = applicationContext
            )
          }
        }
      }
    )
    return Result.success()
  }
}