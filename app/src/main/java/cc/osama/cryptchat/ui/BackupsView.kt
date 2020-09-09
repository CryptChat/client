package cc.osama.cryptchat.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log.d
import android.util.Log.e
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import cc.osama.cryptchat.AsyncExec
import cc.osama.cryptchat.Cryptchat
import cc.osama.cryptchat.Database
import cc.osama.cryptchat.R
import kotlinx.android.synthetic.main.activity_backups_view.*
import java.io.*
import java.lang.Exception
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class BackupsView: AppCompatActivity() {
  companion object {
    private const val BACKUPS_TREE_REQUEST_CODE = 362
    private const val BACKUPS_RESTORE_REQUEST_CODE = 369
    fun createIntent(context: Context) = Intent(context, BackupsView::class.java)
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_backups_view)
    setSupportActionBar(backupsViewToolbar)
    restoreBackupButton.setOnClickListener {
      Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
        addCategory(Intent.CATEGORY_OPENABLE)
        flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        type = "application/octet-stream"
        startActivityForResult(this, BACKUPS_RESTORE_REQUEST_CODE)
      }
    }
    takeBackupButton.setOnClickListener {
      AlertDialog.Builder(this).apply {
        setNegativeButton(R.string.dialog_cancel) { _, _ -> }
        setPositiveButton(R.string.dialog_yes) { _, _ ->
          val allowedTreeUri = currentBackupsDirectory()
          if (allowedTreeUri != null) {
            createBackup(allowedTreeUri)
          } else {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
            startActivityForResult(intent, BACKUPS_TREE_REQUEST_CODE)
          }
        }
        setMessage(R.string.backup_process_confirmation_message)
        create().show()
      }
    }
  }

  override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    super.onActivityResult(requestCode, resultCode, data)
    if (resultCode == Activity.RESULT_OK) {
      if (data != null) {
        val uri = data.data
        if (uri != null) {
          when (requestCode) {
            BACKUPS_TREE_REQUEST_CODE -> {
              contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
              createBackup(uri)
            }
            BACKUPS_RESTORE_REQUEST_CODE -> {
              restoreBackup(uri)
            }
          }
        } else {
          d("BackupsView", "onActivityResult returned because uri is null.")
        }
      } else {
        d("BackupsView", "onActivityResult returned because data is null.")
      }
    } else {
      d("BackupsView", "onActivityResult returned result code was not OK. resultCode=$resultCode")
    }
  }

  private fun writeBackupToStream(outS: OutputStream) {
    val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
    val key = ByteArray(32)
    SecureRandom().nextBytes(key)
    cipher.init(
      Cipher.ENCRYPT_MODE,
      SecretKeySpec(key, "AES"),
      IvParameterSpec(ByteArray(16))
    )
    Cryptchat.db(applicationContext).checkpoint()
    Cryptchat.enableReadonly(applicationContext)
    FileInputStream(getDatabasePath(Database.Name)).use { input ->
      CipherOutputStream(outS, cipher).use { cipherOut ->
        val buffer = ByteArray(1024)
        var bytesRead = input.read(buffer)
        while (bytesRead != -1) {
          cipherOut.write(buffer, 0, bytesRead)
          bytesRead = input.read(buffer)
        }
      }
    }
  }

  private fun createBackup(treeUri: Uri) {
    AsyncExec.run {
      val documentFile = DocumentFile.fromTreeUri(this, treeUri)
      if (documentFile != null) {
        val file = documentFile.createFile(
          "application/octet-stream",
          "${System.currentTimeMillis()}-cryptchat-backup.enc"
        )
        val fileUri = file?.uri
        try {
          if (fileUri != null) {
            val stream = contentResolver.openOutputStream(fileUri)
            if (stream != null) {
              writeBackupToStream(stream)
            } else {
              d("BackupsView", "createBackup returned because stream is null.")
            }
          } else {
            d("BackupsView", "createBackup returned because fileUri is null.")
          }
        } catch (ex: Exception) {
          e("BackupsView", "Backup failed", ex)
          file?.delete()
        }
      } else {
        d("BackupsView", "createBackup returned because documentFile is null.")
      }
    }
  }

  private fun restoreBackup(backupUri: Uri) {
    AsyncExec.run {
      Cryptchat.db(applicationContext).checkpoint()
      Cryptchat.enableReadonly(applicationContext)
      val file = getDatabasePath(Database.Name)
      file.parentFile?.listFiles()?.forEach {
        if (it.name == "${Database.Name}-wal" || it.name == "${Database.Name}-shm") {
          d("TEST", "DELETED ${it.absolutePath}")
          it.delete()
        }
      }
      file.delete()
      val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
      val key = ByteArray(32)
      SecureRandom().nextBytes(key)
      cipher.init(
        Cipher.DECRYPT_MODE,
        SecretKeySpec(key, "AES"),
        IvParameterSpec(ByteArray(16))
      )
      CipherInputStream(contentResolver.openInputStream(backupUri), cipher).use { cipherStream ->
        FileOutputStream(file).use { outputStream ->
          val buffer = ByteArray(1024)
          var bytesRead = cipherStream.read(buffer)
          while (bytesRead != -1) {
            outputStream.write(buffer, 0, bytesRead)
            bytesRead = cipherStream.read(buffer)
          }
        }
      }
      Cryptchat.disableReadonly(applicationContext)
      if (!Cryptchat.db(applicationContext).openHelper.writableDatabase.isDatabaseIntegrityOk) {
        e("BackupsView", "DATABASE INTEGRITY CHECK FAILED")
      }
    }
  }

  private fun currentBackupsDirectory() : Uri? {
    val permissions = contentResolver.persistedUriPermissions
    return if (permissions.size > 0) {
      permissions[0].uri
    } else {
      null
    }
  }
}
