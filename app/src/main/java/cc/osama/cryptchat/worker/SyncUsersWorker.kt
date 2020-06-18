package cc.osama.cryptchat.worker

import android.content.Context
import android.content.Intent
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.work.*
import cc.osama.cryptchat.*
import cc.osama.cryptchat.db.User
import cc.osama.cryptchat.ui.ServerUsersList
import org.json.JSONArray
import org.json.JSONObject
import java.lang.Exception
import java.lang.IllegalArgumentException

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
    val maxLastUpdatedAt = db.users().findMaxLastUpdatedAtOnServer(server.id) ?: 0

    CryptchatServer(applicationContext, server).post(
      path = "/sync/users.json",
      param = JSONObject().also { it.put("updated_at", maxLastUpdatedAt) },
      success = {
        val usersJsonArray = it["users"] as? JSONArray ?: return@post
        val users = mutableListOf<User>()
        AsyncExec.run {
          for (i in 0 until usersJsonArray.length()) {
            val userJson = usersJsonArray[i] as? JSONObject ?: continue
            var publicKey: ECPublicKey?
            try {
              publicKey = if (userJson["identity_key"] as? String != null) ECPublicKey(userJson["identity_key"] as String) else null
            } catch (ex: IllegalArgumentException) {
              continue
            }
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
              lastUpdatedAt != null
            ) {
              val existingUser = db.users().findUserByServerIdAndIdOnServer(server.id, idOnServer)
              if (existingUser == null) {
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
              } else {
                existingUser.countryCode = countryCode
                existingUser.lastUpdatedAt = lastUpdatedAt
                existingUser.phoneNumber = phoneNumber
                existingUser.name = name
                db.users().update(existingUser)
              }
            }
          }
          db.users().addMany(users)
          LocalBroadcastManager.getInstance(applicationContext).also { broadcast ->
            val intent = Intent(ServerUsersList.REFRESH_COMMAND)
            broadcast.sendBroadcast(intent)
          }
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