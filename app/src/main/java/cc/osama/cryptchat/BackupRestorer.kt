package cc.osama.cryptchat

import android.content.Context
import android.net.Uri
import android.util.Log.d
import android.util.Log.e
import cc.osama.cryptchat.ui.RestoreBackup
import cc.osama.cryptchat.worker.InstanceIdsManagerWorker
import cc.osama.cryptchat.worker.SyncUsersWorker
import java.io.FileOutputStream
import java.io.InputStream
import java.lang.Exception
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class BackupRestorer(
  private val applicationContext: Context,
  private val uri: Uri,
  var activity: RestoreBackup? = null
) {
  companion object {
    private class BadDecryptException() : Exception()
  }
  private var progress: Double = 0.0
  private var error: String? = null

  fun getError() = error
  fun getProgress() = progress

  fun start(password: String) {
    AsyncExec.run(AsyncExec.Companion.Threads.Network) {
      val file = applicationContext.getDatabasePath(Database.Name)
      var inputStream: InputStream? = null
      try {
        Cryptchat.db(applicationContext).checkpoint()
        file.parentFile?.listFiles()?.forEach { childFile ->
          if (childFile.name == "${Database.Name}-wal" ||
            childFile.name == "${Database.Name}-shm"
          ) {
            childFile.delete()
          }
        }
        file.delete()
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        var key = password.toByteArray(Charsets.UTF_8)
        val sha = MessageDigest.getInstance("SHA-256")
        repeat(100_000) {
          key = sha.digest(key)
        }
        inputStream = applicationContext.contentResolver.openInputStream(uri)
        if (inputStream == null) {
          d("BackupRestorer", "restoreBackup returned cuz fileInputStream was null. uri=$uri")
          error = applicationContext.resources.getString(R.string.backup_restorer_failed_unexpected_condition)
          return@run
        }
        val ivBytes = ByteArray(BackupCreator.IV_SIZE)
        val readIvBytes = inputStream.read(ivBytes, 0, ivBytes.size)
        if (readIvBytes != BackupCreator.IV_SIZE) {
          e("BackupRestorer", "expected to read ${BackupCreator.IV_SIZE} iv bytes, instead got $readIvBytes bytes.")
          error = applicationContext.resources.getString(R.string.backup_restorer_failed_short_iv)
          return@run
        }
        var fileSize: Long = 0
        inputStream.use { stream ->
          val buffer = ByteArray(16384)
          var bytesRead = stream.read(buffer)
          while (bytesRead != -1) {
            fileSize += bytesRead
            bytesRead = stream.read(buffer)
          }
        }
        inputStream = applicationContext.contentResolver.openInputStream(uri)
        if (inputStream == null) {
          d("BackupRestorer", "restoreBackup returned cuz fileInputStream was null<2>. uri=$uri")
          error = applicationContext.resources.getString(R.string.backup_restorer_failed_unexpected_condition)
          return@run
        }
        inputStream.skip(BackupCreator.IV_SIZE.toLong())

        cipher.init(
          Cipher.DECRYPT_MODE,
          SecretKeySpec(key, "AES"),
          IvParameterSpec(ivBytes)
        )
        CipherInputStream(inputStream, cipher).use { cipherInputStream ->
          FileOutputStream(file).use { fileOutputStream ->
            val buffer = ByteArray(16384)
            var bytesRead = cipherInputStream.read(buffer)
            val sqliteDbHeader = String(buffer.copyOfRange(0, 16), Charsets.UTF_8)
            if (sqliteDbHeader != "SQLite format 3\u0000") {
              throw BadDecryptException()
            }
            var totalBytesRead = bytesRead.toLong()
            while (bytesRead != -1) {
              fileOutputStream.write(buffer, 0, bytesRead)
              progress = (totalBytesRead / fileSize.toDouble()) * 100
              AsyncExec.onUiThread {
                activity?.notifyRestoreProgress(this)
              }
              bytesRead = cipherInputStream.read(buffer)
              totalBytesRead += bytesRead
            }
          }
        }
        if (!Cryptchat.db(applicationContext).openHelper.writableDatabase.isDatabaseIntegrityOk) {
          if (file != null && file.exists()) file.delete()
          e("BackupsView", "DATABASE INTEGRITY CHECK FAILED")
          error = applicationContext.resources.getString(R.string.backup_restorer_failed_bad_db_integrity)
        }
      } catch (ex: BadDecryptException) {
        if (file != null && file.exists()) file.delete()
        error = applicationContext.resources.getString(R.string.backup_restorer_failed_decryption_failed)
      } catch (ex: Exception) {
        if (file != null && file.exists()) file.delete()
        e("BackupRestorer", "EXCEPTION OCCURRED", ex)
        error = applicationContext.resources.getString(R.string.restore_backup_restore_failed, ex.toString())
      } finally {
        inputStream?.close()
        Cryptchat.disableReadonly(applicationContext)
        AsyncExec.onUiThread {
          activity?.notifyRestoreComplete(this)
        }
      }
      if (error == null) {
        InstanceIdsManagerWorker.enqueue(applicationContext)
        Cryptchat.db(applicationContext).servers().getAll().forEach {
          SyncUsersWorker.enqueue(it.id, true, applicationContext)
          CryptchatServer(applicationContext, it).request(
            CryptchatRequest.Methods.GET,
            "/my-avatar.json",
            async = false,
            success = { json ->
              val url = CryptchatUtils.jsonOptString(json, "url")
              if (url != null) {
                AvatarsStore(it.id, null, applicationContext).download(
                  it.urlForPath(url),
                  applicationContext.resources
                )
              }
            },
            failure = { error ->
              e("BackupRestorer", "Failed to download self avatar. serverId=${it.id}, error=$error")
            }
          )
        }
      }
    }
  }
}