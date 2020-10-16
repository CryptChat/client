package cc.osama.cryptchat

import android.content.Context
import android.net.Uri
import android.util.Log.d
import android.util.Log.e
import androidx.documentfile.provider.DocumentFile
import cc.osama.cryptchat.ui.TakeBackup
import java.io.*
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.CipherOutputStream
import javax.crypto.spec.SecretKeySpec

class BackupCreator(
  private val applicationContext: Context,
  private val uri: Uri,
  var activity: TakeBackup? = null
) {
  companion object {
    const val IV_SIZE = 12
    const val AAD_SIZE = 16
    const val TAG_SIZE = 16
    const val MIME_TYPE = "application/octet-stream"
  }

  private var progress: Double = 0.0
  private var error: String? = null

  fun getProgress() = progress
  fun getError() = error

  fun start(password: String) {
    Cryptchat.enableReadonly(applicationContext)
    AsyncExec.run {
      // sleep to allow for any running background tasks to finish
      // and execute any DB queries they might have.
      Thread.sleep(3000)
      val documentTree = DocumentFile.fromTreeUri(applicationContext, uri)
      var file: DocumentFile? = null
      try {
        file = documentTree?.createFile(
          MIME_TYPE,
          "${System.currentTimeMillis()}-cryptchat-backup.enc"
        )
        val fileUri = file?.uri
        if (fileUri != null) {
          val stream = applicationContext.contentResolver.openOutputStream(fileUri)
          if (stream != null) {
            writeBackup(stream, password) { complete, total ->
              progress = (complete.toDouble() / total) * 100
              it.execMainThread {
                activity?.notifyBackupProgress(this)
              }
            }
            it.execMainThread {
              activity?.notifyBackupComplete(this)
            }
            return@run
          }
        }
      } catch (ex: Exception) {
        file?.delete()
        e("BackupCreator", "Backup failed", ex)
        Cryptchat.disableReadonly(applicationContext)
        error = applicationContext.resources.getString(
          R.string.backup_creator_failed_with_exception,
          ex.toString()
        )
        it.execMainThread {
          activity?.notifyBackupComplete(this)
        }
        return@run
      }
      file?.delete()
      d("BackupCreator", "start returned because fileUri or stream are null.")
      Cryptchat.disableReadonly(applicationContext)
      error = applicationContext.resources.getString(R.string.backup_creator_failed_unexpected_condition)
      it.execMainThread {
        activity?.notifyBackupComplete(this)
      }
    }
  }

  private fun writeBackup(
    outputStream: OutputStream,
    password: String,
    callback: (Long, Long) -> Unit
  ) {
    val sha = MessageDigest.getInstance("SHA-256")
    var key = password.toByteArray(Charsets.UTF_8)
    repeat(100_000) {
      key = sha.digest(key)
    }
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"))
    val iv = cipher.iv.copyOf()
    val aad = SecureRandom().generateSeed(AAD_SIZE)
    cipher.updateAAD(aad)
    Cryptchat.db(applicationContext).checkpoint()
    val dbFile = applicationContext.getDatabasePath(Database.Name)
    val size = dbFile.length()
    outputStream.write(iv, 0, iv.size)
    outputStream.write(aad, 0, aad.size)
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