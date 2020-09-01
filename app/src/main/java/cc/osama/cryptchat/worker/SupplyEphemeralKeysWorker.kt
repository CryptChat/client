package cc.osama.cryptchat.worker

import android.content.Context
import android.util.Log.d
import android.util.Log.e
import androidx.work.*
import cc.osama.cryptchat.*
import cc.osama.cryptchat.db.EphemeralKey
import org.json.JSONArray
import org.json.JSONObject

class SupplyEphemeralKeysWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
  companion object {
    fun enqueue(serverId: Long, batchSize: Int, context: Context) {
      val workerArgs = Data.Builder().apply {
        putLong("serverId", serverId)
        putInt("batchSize", batchSize)
      }.build()
      val request = OneTimeWorkRequestBuilder<InstanceIdsManagerWorker>()
        .setInputData(workerArgs)
        .build()
      WorkManager.getInstance(context).enqueue(request)
    }
  }
  override fun doWork(): Result {
    val serverId = inputData.getLong("serverId", -1)
    val batchSize = inputData.getInt("batchSize", 500)
    val db = Cryptchat.db(applicationContext)
    val server = db.servers().findById(serverId)
    if (server == null) {
      d("SupplyEphKeysWorker", "server is null; serverId is $serverId")
      return Result.success()
    }
    val ephemeralKeysList = mutableListOf<EphemeralKey>()
    for (i in 1..batchSize) {
      val keyPair = CryptchatSecurity.genKeyPair()
      ephemeralKeysList.add(
        EphemeralKey(
          serverId = server.id,
          publicKey = keyPair.publicKey.toString(),
          privateKey = keyPair.privateKey.toString()
        )
      )
    }
    val ids = db.ephemeralKeys().addMany(ephemeralKeysList)
    val keysList = db.ephemeralKeys().findByIds(ids)
    val jsonArray = JSONArray()
    keysList.forEach { key ->
      JSONObject().apply {
        put("id", key.id)
        put("key", key.publicKey)
        jsonArray.put(this)
      }
    }
    val params = JSONObject()
    params.put("keys", jsonArray)
    CryptchatServer(applicationContext, server).request(
      async = false,
      method = CryptchatRequest.Methods.POST,
      path = "/ephemeral-keys.json",
      param = params,
      success = {
        d("SupplyEphKeysWorker", "Request completed successfully.")
      },
      failure = { error ->
        e("SupplyEphKeysWorker", "Request failed $error", error.originalError)
        db.ephemeralKeys().deleteMany(ephemeralKeysList)
      }
    )
    return Result.success()
  }
}