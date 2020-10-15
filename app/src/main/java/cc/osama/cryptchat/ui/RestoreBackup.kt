package cc.osama.cryptchat.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log.d
import android.util.TypedValue
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import cc.osama.cryptchat.*
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.android.synthetic.main.activity_restore_backup.*
import kotlin.math.roundToInt


class RestoreBackup: CryptchatBaseAppCompatActivity() {
  companion object {
    private const val BACKUPS_RESTORE_REQUEST_CODE = 943
    private var runningBackupRestore: BackupRestorer? = null
    fun createIntent(context: Context) = Intent(context, RestoreBackup::class.java)
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_restore_backup)
    setSupportActionBar(restoreBackupToolbar)
    supportActionBar?.setDisplayHomeAsUpEnabled(true)
    startBackupRestoreButton.setOnClickListener {
      Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
        addCategory(Intent.CATEGORY_OPENABLE)
        flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        type = BackupCreator.MIME_TYPE
        startActivityForResult(this, BACKUPS_RESTORE_REQUEST_CODE)
      }
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

  override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    super.onActivityResult(requestCode, resultCode, data)
    if (resultCode == Activity.RESULT_OK) {
      if (data != null) {
        val uri = data.data
        if (uri != null) {
          when (requestCode) {
            BACKUPS_RESTORE_REQUEST_CODE -> {
              MaterialAlertDialogBuilder(this).apply {
                val view = layoutInflater.inflate(R.layout.backup_password_dialog, null)
                val input = view.findViewById<EditText>(R.id.backups_dialog_password)
                setView(view)
                setTitle(R.string.take_backup_view_password_dialog_title)
                setNegativeButton(android.R.string.cancel) { _, _ -> }
                setPositiveButton(android.R.string.ok) { _, _ ->
                  val password = input.text.toString().trim()
                  startBackupRestore(uri, password)
                }
                val dialog = create()
                dialog.show()
                val positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                positiveButton?.isEnabled = false
                input?.addTextChangedListener(CryptchatTextWatcher(
                  on = { sequence, _, _, _ ->
                    positiveButton?.isEnabled = sequence != null && sequence.trim().length >= 10
                  }
                ))
              }
            }
          }
        } else {
          d("RestoreBackup", "onActivityResult returned because uri is null.")
        }
      } else {
        d("RestoreBackup", "onActivityResult returned because data is null.")
      }
    } else {
      d("RestoreBackup", "onActivityResult returned result code was not OK. resultCode=$resultCode")
    }
  }

  override fun onStart() {
    super.onStart()
    runningBackupRestore?.let {
      it.activity = this
      applyRestoreInProgressState(it)
    }
  }

  override fun onStop() {
    super.onStop()
    runningBackupRestore?.activity = null
  }

  fun notifyRestoreComplete(br: BackupRestorer) {
    val error = br.getError()
    backupRestoreProgressbar.visibility = View.INVISIBLE
    if (error == null) {
      backupRestoreStatus.setText(R.string.restore_backup_restore_succeeded)
    } else {
      backupRestoreStatus.text = error
      obtainStyledAttributes(TypedValue().data, intArrayOf(R.attr.colorError)).also { ta ->
        backupRestoreStatus.setTextColor(ta.getColor(0, 0))
      }.recycle()
    }
    startBackupRestoreButton.isEnabled = true
    runningBackupRestore = null
  }

  fun notifyRestoreProgress(br: BackupRestorer) {
    backupRestoreStatus.text = resources.getString(R.string.restore_backup_restore_progress, br.getProgress())
    backupRestoreProgressbar.progress = br.getProgress().roundToInt()
  }

  private fun startBackupRestore(uri: Uri, password: String) {
    val br = BackupRestorer(applicationContext, uri, this)
    runningBackupRestore = br
    br.start(password)
    applyRestoreInProgressState(br)
  }

  private fun applyRestoreInProgressState(br: BackupRestorer) {
    backupRestoreStatus.visibility = View.VISIBLE
    backupRestoreProgressbar.visibility = View.VISIBLE
    startBackupRestoreButton.isEnabled = false
    obtainStyledAttributes(TypedValue().data, intArrayOf(android.R.attr.textColorTertiary)).also { ta ->
      backupRestoreStatus.setTextColor(ta.getColor(0, 0))
    }.recycle()
    if (br.getError() != null || br.getProgress() == 100.0) {
      notifyRestoreComplete(br)
    } else {
      notifyRestoreProgress(br)
    }
  }
}
