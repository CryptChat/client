package cc.osama.cryptchat.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log.d
import android.util.Log.e
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.*
import cc.osama.cryptchat.*
import cc.osama.cryptchat.R
import kotlinx.android.synthetic.main.activity_backups_entry.*
import java.io.FileOutputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class BackupsEntry: AppCompatActivity() {
  companion object {
    private const val BACKUPS_TREE_REQUEST_CODE = 382
    fun createIntent(context: Context) = Intent(context, BackupsEntry::class.java)
  }

  class SettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
      addPreferencesFromResource(R.xml.backup_settings)
      val backupsLocationSetting = findPreference<Preference>("backups_location_setting")
      val readonlyModeSettings = findPreference<SwitchPreference>("readonly_mode_setting")
      val takeBackup = findPreference<Preference>("take_backup_action")
      if (backupsLocationSetting == null) {
        d("BackupsView\$Fragment", "onCreatePreferences returned because backupsLocationSetting is null")
        return
      }
      if (readonlyModeSettings == null) {
        d("BackupsView\$Fragment", "onCreatePreferences returned because readonlyModeSettings is null")
        return
      }
      if (takeBackup == null) {
        d("BackupsView\$Fragment", "onCreatePreferences returned because takeBackup is null")
        return
      }
      readonlyModeSettings.summary = resources.getString(R.string.backups_entry_view_readonly_mode_summary)
      (activity as? BackupsEntry)?.let {
        Cryptchat.backupsTreeUri(it.applicationContext)?.also { uri ->
          updateBackupsLocation(uri)
        }
        readonlyModeSettings.isChecked = Cryptchat.isReadonly(it.applicationContext)
        readonlyModeSettings.isEnabled = readonlyModeSettings.isChecked && !TakeBackup.isBackupInProgress()
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
      takeBackup.setOnPreferenceClickListener {
        activity?.let {
          it.startActivity(TakeBackup.createIntent(it))
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
      (activity as? BackupsEntry)?.let {
        val parts = uri.path?.split(":")
        val name = if (parts?.size == 2) parts[1] else uri.path
        if (name != null) {
          backupsLocationSetting.summary = "/$name"
        } else {
          backupsLocationSetting.summary = resources.getText(R.string.backups_entry_view_backups_location_unset)
        }
      }
    }
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_backups_entry)
    setSupportActionBar(backupsEntryToolbar)
    supportActionBar?.setDisplayHomeAsUpEnabled(true)
    supportActionBar?.title = resources.getString(R.string.servers_list_menu_backups)
    // restoreBackupButton.setOnClickListener {
    //   Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
    //     addCategory(Intent.CATEGORY_OPENABLE)
    //     flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
    //     type = "application/octet-stream"
    //     startActivityForResult(this, BACKUPS_RESTORE_REQUEST_CODE)
    //   }
    // }
  }

  override fun onStart() {
    super.onStart()
    supportFragmentManager
      .beginTransaction()
      .replace(R.id.backupsViewFragHolder, SettingsFragment())
      .commit()
  }

  override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    super.onActivityResult(requestCode, resultCode, data)
    if (resultCode == Activity.RESULT_OK) {
      if (data != null) {
        val uri = data.data
        if (uri != null) {
          when (requestCode) {
            BACKUPS_TREE_REQUEST_CODE -> {
              Cryptchat.setBackupsDir(applicationContext, uri)
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

  private fun settingsFrag(callback: SettingsFragment.() -> Unit) {
    val frag = supportFragmentManager.findFragmentById(R.id.backupsViewFragHolder) as? SettingsFragment
    if (frag != null) {
      callback(frag)
    } else {
      d("BackupsView", "settingsFrag returned cuz frag is null. ${Thread.currentThread().stackTrace[0]}")
    }
  }
}
