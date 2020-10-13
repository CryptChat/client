package cc.osama.cryptchat.worker

import android.content.Context
import androidx.core.content.edit
import androidx.work.*
import cc.osama.cryptchat.*
import cc.osama.cryptchat.db.User
import cc.osama.cryptchat.ui.ServerUsersList
import org.json.JSONObject
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

    fun lastRunTime(context: Context, serverId: Long) : Long {
      val key = "sync_users_last_ran_server:${serverId}"
      return Cryptchat.sharedPreferences(context).getLong(key, -1)
    }

    private fun updateLastRan(context: Context, serverId: Long) {
      val key = "sync_users_last_ran_server:${serverId}"
      Cryptchat.sharedPreferences(context).edit {
        putLong(key, System.currentTimeMillis())
        commit()
      }
    }
  }

  override fun doWork() : Result {
    val serverId = inputData.getLong("serverId", -1)
    val scheduleMessagesSync = inputData.getBoolean("scheduleMessagesSync", false)
    val db = Cryptchat.db(applicationContext)
    val server = db.servers().findById(serverId) ?: return Result.success()
    // unit is ms since epoch
    val maxLastUpdatedAt = (db.users().findMaxLastUpdatedAtOnServer(server.id) ?: 0) - 10

    CryptchatServer(applicationContext, server).request(
      method = CryptchatRequest.Methods.POST,
      path = "/sync/users.json",
      param = JSONObject().also { it.put("updated_at", maxLastUpdatedAt) },
      async = false,
      success = {
        val usersJsonArray = it.optJSONArray("users") ?: return@request
        for (i in 0 until usersJsonArray.length()) {
          val userJson = usersJsonArray[i] as? JSONObject ?: continue
          var publicKey: ECPublicKey?
          try {
            val identityKey = CryptchatUtils.jsonOptString(userJson, "identity_key")
            if (identityKey == null || identityKey.isEmpty()) continue
            publicKey = ECPublicKey(identityKey)
          } catch (ex: IllegalArgumentException) {
            continue
          }
          val countryCode = CryptchatUtils.jsonOptString(userJson, "country_code")
          val phoneNumber = CryptchatUtils.jsonOptString(userJson, "phone_number")
          val idOnServer = userJson.optLong("id", -1).let { id ->
            if (id != (-1).toLong()) id else null
          }
          if (idOnServer == server.userId) continue
          val lastUpdatedAt = userJson.optLong("updated_at", -1).let { u ->
            if (u != (-1).toLong()) u else null
          }
          val name = CryptchatUtils.jsonOptString(userJson,  "name")
          val avatarUrl = CryptchatUtils.jsonOptString(userJson, "avatar_url")
          if (countryCode != null &&
            phoneNumber != null &&
            idOnServer != null &&
            lastUpdatedAt != null
          ) {
            val existingUser = db.users().findUserByServerIdAndIdOnServer(server.id, idOnServer)
            if (existingUser == null) {
              val id = db.users().add(
                User(
                  serverId = server.id,
                  publicKey = publicKey,
                  lastUpdatedAt = lastUpdatedAt,
                  phoneNumber = phoneNumber,
                  countryCode = countryCode,
                  idOnServer = idOnServer,
                  name = name,
                  avatarUrl = avatarUrl
                )
              )
              if (avatarUrl != null) {
                AvatarsStore(
                  serverId = server.id,
                  userId = id,
                  context = applicationContext
                ).download(server.urlForPath(avatarUrl), applicationContext.resources)
              }
            } else {
              var changed = false
              if (existingUser.countryCode != countryCode) {
                existingUser.countryCode = countryCode
                changed = true
              }
              if (existingUser.lastUpdatedAt != lastUpdatedAt) {
                existingUser.lastUpdatedAt = lastUpdatedAt
                changed = true
              }
              if (existingUser.phoneNumber != phoneNumber) {
                existingUser.phoneNumber = phoneNumber
                changed = true
              }
              if (existingUser.name != name) {
                existingUser.name = name
                changed = true
              }
              if (existingUser.avatarUrl != avatarUrl) {
                existingUser.avatarUrl = avatarUrl
                changed = true
              }
              if (changed) {
                db.users().update(existingUser)
              }
              if (avatarUrl == null) {
                AvatarsStore(server.id, existingUser.id, applicationContext).delete()
              } else {
                AvatarsStore(
                  serverId = server.id,
                  userId = existingUser.id,
                  context = applicationContext
                ).download(server.address + avatarUrl, applicationContext.resources)
              }
            }
          }
        }
        updateLastRan(applicationContext, server.id)
        ServerUsersList.refreshUsersList(applicationContext)
        if (scheduleMessagesSync) {
          SyncMessagesWorker.enqueue(
            serverId = server.id,
            context = applicationContext
          )
        }
      }
    )
    return Result.success()
  }
}