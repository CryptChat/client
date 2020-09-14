package cc.osama.cryptchat

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import cc.osama.cryptchat.ui.BackupsView
import java.io.FileInputStream
import java.io.OutputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.CipherOutputStream
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class BackupCreator(
  private val applicationContext: Context,
  private val uri: Uri,
  var activity: BackupsView? = null
) {
  var progress: Double = 0.0

  fun start() {
    Cryptchat.enableReadonly(applicationContext)
    AsyncExec.run {
      // sleep to allow for any running background tasks to finish
      // and execute any DB queries they might have.
      Thread.sleep(3000)
      val documentFile = DocumentFile.fromTreeUri(applicationContext, uri)
      val file = documentFile?.createFile(
        "application/octet-stream",
        "${System.currentTimeMillis()}-cryptchat-backup.enc"
      )
      val fileUri = file?.uri
      if (fileUri != null) {
        try {
          val stream = applicationContext.contentResolver.openOutputStream(fileUri)
          if (stream != null) {
            writeBackup(stream) { complete, total ->
              progress = (complete.toDouble() / total) * 100
              it.execMainThread {
                activity?.onBackupProgress()
              }
            }
            it.execMainThread {
              activity?.onBackupSuccess()
            }
          } else {
            file.delete()
            Log.d("BackupCreator", "start returned because stream is null.")
            Cryptchat.disableReadonly(applicationContext)
            it.execMainThread {
              activity?.onBackupFailure(
                applicationContext.resources.getString(R.string.backup_creator_failed_unexpected_condition)
              )
            }
          }
        } catch (ex: Exception) {
          file.delete()
          Log.e("BackupCreator", "Backup failed", ex)
          Cryptchat.disableReadonly(applicationContext)
          it.execMainThread {
            activity?.onBackupFailure(
              applicationContext.resources.getString(
                R.string.backup_creator_failed_with_exception,
                ex.toString()
              )
            )
          }
        }
      } else {
        file?.delete()
        Log.d("BackupCreator", "start returned because fileUri is null.")
        Cryptchat.disableReadonly(applicationContext)
        it.execMainThread {
          activity?.onBackupFailure(
            applicationContext.resources.getString(R.string.backup_creator_failed_unexpected_condition)
          )
        }
      }
    }
  }

  private fun writeBackup(
    outputStream: OutputStream,
    callback: (Long, Long) -> Unit
  ) {
    val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
    val key = ByteArray(32)
    SecureRandom().nextBytes(key)
    cipher.init(
      Cipher.ENCRYPT_MODE,
      SecretKeySpec(key, "AES"),
      IvParameterSpec(ByteArray(16))
    )
    Cryptchat.db(applicationContext).checkpoint()
    val dbFile = applicationContext.getDatabasePath(Database.Name)
    val size = dbFile.length()
    FileInputStream(dbFile).use { inputStream ->
      CipherOutputStream(outputStream, cipher).use { cipherOutputStream ->
        val buffer = ByteArray(16384)
        var bytesRead = inputStream.read(buffer)
        var totalBytesRead: Long = bytesRead.toLong()
        while (bytesRead != -1) {
          cipherOutputStream.write(buffer, 0, bytesRead)
          callback(totalBytesRead, size)
          bytesRead = inputStream.read(buffer)
          totalBytesRead += bytesRead
        }
      }
    }
  }
}