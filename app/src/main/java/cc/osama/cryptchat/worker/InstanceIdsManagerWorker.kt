package cc.osama.cryptchat.worker

import android.content.Context
import android.util.Log.d
import android.util.Log.e
import androidx.work.*
import cc.osama.cryptchat.AsyncExec
import cc.osama.cryptchat.Cryptchat
import cc.osama.cryptchat.CryptchatRequest
import cc.osama.cryptchat.CryptchatServer
import com.google.firebase.iid.FirebaseInstanceId
import org.json.JSONObject
import java.io.IOException

class InstanceIdsManagerWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
  companion object {
    fun enqueue(context: Context) {
      val workerArgs = Data.Builder().build()
      val request = OneTimeWorkRequestBuilder<InstanceIdsManagerWorker>()
        .setInputData(workerArgs)
        .build()
      WorkManager.getInstance(context).enqueue(request)
    }
  }
  override fun doWork() : Result {
    Cryptchat.db(applicationContext).also { db ->
      db.servers().getAll().forEach { server ->
        val instanceId: String? = try {
          FirebaseInstanceId.getInstance().getToken(server.senderId, "FCM")
        } catch (ex: IOException) {
          e("INSTANCE ID", "FAILED TO ACQUIRE INSTANCE ID. $ex")
          null
        }
        if (instanceId != null && instanceId != server.instanceId) {
          server.instanceId = instanceId
          val params = JSONObject().also { params ->
            params.put("user", JSONObject().also { user ->
              user.put("instance_id", instanceId)
            })
          }
          CryptchatServer(applicationContext, server).request(
            method = CryptchatRequest.Methods.PUT,
            path = "/users.json",
            param = params,
            async = false,
            success = {
              db.servers().update(server)
            },
            failure = {
              e("INSTANCE ID", "FAILED TO UPDATE INSTANCE ID ON SERVER. $it")
            }
          )
        } else {
          d("InstanceIdsManager", "Skipped pushing key to server. instanceId=$instanceId, server.instanceId=${server.instanceId}")
        }
      }
    }
    return Result.success()
  }
}