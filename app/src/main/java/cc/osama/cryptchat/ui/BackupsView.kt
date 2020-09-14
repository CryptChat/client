package cc.osama.cryptchat.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log.d
import android.util.Log.e
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreference
import cc.osama.cryptchat.*
import kotlinx.android.synthetic.main.activity_backups_view.*
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.OutputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class BackupsView: AppCompatActivity() {
  companion object {
    private const val BACKUPS_TREE_REQUEST_AND_BACKUP_CODE = 362
    private const val BACKUPS_TREE_REQUEST_CODE = 382
    private const val BACKUPS_RESTORE_REQUEST_CODE = 369
    fun createIntent(context: Context) = Intent(context, BackupsView::class.java)
    private var currentBackupCreator: BackupCreator? = null
  }

  class SettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
      addPreferencesFromResource(R.xml.backup_settings)
      val backupsLocationSetting = findPreference<Preference>("backups_location_setting")
      val readonlyModeSettings = findPreference<SwitchPreference>("readonly_mode_setting")
      if (backupsLocationSetting == null) {
        d("BackupsView\$Fragment", "onCreatePreferences returned because backupsLocationSetting is null")
        return
      }
      if (readonlyModeSettings == null) {
        d("BackupsView\$Fragment", "onCreatePreferences returned because readonlyModeSettings is null")
        return
      }
      readonlyModeSettings.summary = resources.getString(R.string.backup_settings_view_readonly_mode_summary)
      (activity as? BackupsView)?.let {
        it.currentBackupsDirectory()?.let { uri ->
          updateBackupsLocation(uri)
        }
        readonlyModeSettings.isEnabled = Cryptchat.isReadonly(it.applicationContext)
        readonlyModeSettings.isChecked = readonlyModeSettings.isEnabled
      }
      backupsLocationSetting.setOnPreferenceClickListener {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        activity?.startActivityForResult(intent, BACKUPS_TREE_REQUEST_CODE)
        true
      }
      readonlyModeSettings.setOnPreferenceChangeListener { _, newValue ->
        val boolean = newValue as Boolean
        if (!boolean) {
          activity?.apply {
            Cryptchat.disableReadonly(applicationContext)
            readonlyModeSettings.isEnabled = false
          }
        }
        true
      }
    }

    fun updateBackupsLocation(uri: Uri) {
      val backupsLocationSetting = findPreference<Preference>("backups_location_setting")
      if (backupsLocationSetting == null) {
        d("BackupsView\$Fragment", "updateBackupsLocation returned because backupsLocationSetting is null")
        return
      }
      (activity as? BackupsView)?.let {
        val parts = uri.path?.split(":")
        val name = if (parts?.size == 2) parts[1] else uri.path
        if (name != null) {
          backupsLocationSetting.summary = "/$name"
        } else {
          backupsLocationSetting.summary = resources.getText(R.string.backup_settings_view_backups_location_unset)
        }
      }
    }
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_backups_view)
    setSupportActionBar(backupsViewToolbar)
    supportActionBar?.setDisplayHomeAsUpEnabled(true)
    supportActionBar?.title = resources.getString(R.string.servers_list_menu_backups)
    supportFragmentManager
      .beginTransaction()
      .add(R.id.backupsViewFragHolder, SettingsFragment())
      .commit()
    // restoreBackupButton.setOnClickListener {
    //   Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
    //     addCategory(Intent.CATEGORY_OPENABLE)
    //     flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
    //     type = "application/octet-stream"
    //     startActivityForResult(this, BACKUPS_RESTORE_REQUEST_CODE)
    //   }
    // }
    takeBackupButton.setOnClickListener {
      AlertDialog.Builder(this).apply {
        setNegativeButton(R.string.dialog_cancel) { _, _ -> }
        setPositiveButton(R.string.dialog_yes) { _, _ ->
          val allowedTreeUri = currentBackupsDirectory()
          if (allowedTreeUri != null) {
            createBackup(allowedTreeUri)
          } else {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
            startActivityForResult(intent, BACKUPS_TREE_REQUEST_AND_BACKUP_CODE)
          }
        }
        setMessage(R.string.backup_process_confirmation_message)
        create().show()
      }
    }
  }

  override fun onStart() {
    super.onStart()
    currentBackupCreator?.let {
      it.activity = this
      applyBackupInProgressState()
      if (it.progress > 0.0) onBackupProgress()
    }
  }

  override fun onPause() {
    super.onPause()
    currentBackupCreator?.activity = null
  }

  override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    super.onActivityResult(requestCode, resultCode, data)
    if (resultCode == Activity.RESULT_OK) {
      if (data != null) {
        val uri = data.data
        if (uri != null) {
          when (requestCode) {
            BACKUPS_TREE_REQUEST_AND_BACKUP_CODE -> {
              contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
              settingsFrag {
                updateBackupsLocation(uri)
              }
              createBackup(uri)
            }
            BACKUPS_RESTORE_REQUEST_CODE -> {
              restoreBackup(uri)
            }
            BACKUPS_TREE_REQUEST_CODE -> {
              currentBackupsDirectory()?.also {
                contentResolver.releasePersistableUriPermission(it, Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
              }
              contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
              settingsFrag {
                updateBackupsLocation(uri)
              }
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

  override fun onOptionsItemSelected(item: MenuItem): Boolean {
    if (item.itemId == android.R.id.home) {
      onBackPressed()
    } else {
      return super.onOptionsItemSelected(item)
    }
    return true
  }

  fun onBackupSuccess() {
    settingsFrag {
      findPreference<SwitchPreference>("readonly_mode_setting")?.also {
        it.isEnabled = true
        it.isChecked = true
      }
    }
    backupStatusIndicator.visibility = View.GONE
    backupProgressBar.visibility = View.GONE
    takeBackupButton.isEnabled = true
    AlertDialog.Builder(this).apply {
      setMessage(R.string.backup_settings_view_backup_successful_backup)
      setPositiveButton(R.string.dialog_ok) { _, _ ->  }
      create().show()
    }
    currentBackupCreator = null
  }

  fun onBackupProgress() {
    applyBackupInProgressState()
    currentBackupCreator?.also {
      backupStatusIndicator.text = resources.getString(
        R.string.backup_settings_view_backup_progress,
        it.progress
      )
    }
  }

  fun onBackupFailure(message: String) {
    settingsFrag {
      findPreference<SwitchPreference>("readonly_mode_setting")?.also {
        it.isEnabled = false
        it.isChecked = false
      }
    }
    backupStatusIndicator.visibility = View.GONE
    backupProgressBar.visibility = View.GONE
    takeBackupButton.isEnabled = true
    AlertDialog.Builder(this).apply {
      setMessage(message)
      setNegativeButton(R.string.dialog_ok) { _, _ ->  }
      create().show()
    }
    currentBackupCreator = null
  }

  private fun createBackup(treeUri: Uri) {
    applyBackupInProgressState()
    BackupCreator(applicationContext, treeUri).let {
      currentBackupCreator = it
      it.activity = this
      it.start()
    }
  }

  private fun applyBackupInProgressState() {
    takeBackupButton.isEnabled = false
    settingsFrag {
      findPreference<SwitchPreference>("readonly_mode_setting")?.also {
        it.isEnabled = false
        it.isChecked = true
      }
    }
    backupStatusIndicator.visibility = View.VISIBLE
    backupProgressBar.visibility = View.VISIBLE
    backupStatusIndicator.text = resources.getString(R.string.backup_settings_view_backup_starting_backup_process)
    settingsFrag {
      findPreference<SwitchPreference>("readonly_mode_setting")?.also { switchPreference ->
        switchPreference.isChecked = true
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

  private fun settingsFrag(callback: SettingsFragment.() -> Unit) {
    val frag = supportFragmentManager.findFragmentById(R.id.backupsViewFragHolder) as? SettingsFragment
    if (frag != null) {
      callback(frag)
    } else {
      d("BackupsView", "settingsFrag returned cuz frag is null. ${Thread.currentThread().stackTrace[0]}")
    }
  }
}
