package cc.osama.cryptchat.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log.d
import android.util.Log.e
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import cc.osama.cryptchat.*
import kotlinx.android.synthetic.main.activity_take_backup.*
import kotlin.math.roundToInt

class TakeBackup : CryptchatBaseAppCompatActivity() {
  companion object {
    private const val BACKUP_TREE_REQUEST_CODE = 395
    private var runningBackupCreator: BackupCreator? = null
    fun createIntent(context: Context) = Intent(context, TakeBackup::class.java)
    fun isBackupInProgress() = runningBackupCreator != null
  }

  private var carriedPassword: String? = null
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_take_backup)
    setSupportActionBar(takeBackupToolbar)
    supportActionBar?.setDisplayHomeAsUpEnabled(true)
    supportActionBar?.title = resources.getString(R.string.take_backup_view_toolbar_title)
    backupStartButton.setOnClickListener {
      AlertDialog.Builder(this).apply {
        val view = layoutInflater.inflate(R.layout.backup_password_dialog, null)
        val input = view.findViewById<EditText>(R.id.backups_dialog_password)
        setView(view)
        setTitle(R.string.take_backup_view_password_dialog_title)
        setMessage(R.string.take_backup_view_password_dialog_password_tip)
        setNegativeButton(android.R.string.cancel) { _, _ -> }
        setPositiveButton(android.R.string.ok) { _, _ ->
          val password = input.text.toString().trim()
          val treeUri = Cryptchat.backupsTreeUri(applicationContext)
          if (treeUri != null) {
            startBackup(treeUri, password)
          } else {
            carriedPassword = password
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
            startActivityForResult(intent, BACKUP_TREE_REQUEST_CODE)
          }
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

  override fun onStart() {
    super.onStart()
    val bc = runningBackupCreator
    if (bc != null) {
      applyBackupInProgressState(bc)
    }
  }

  override fun onPause() {
    super.onPause()
    runningBackupCreator?.activity = null
  }

  override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    super.onActivityResult(requestCode, resultCode, data)
    if (resultCode == Activity.RESULT_OK) {
      if (data != null) {
        val uri = data.data
        if (uri != null) {
          when (requestCode) {
            BACKUP_TREE_REQUEST_CODE -> {
              Cryptchat.setBackupsDir(applicationContext, uri)
              val password = carriedPassword
              carriedPassword = null
              if (password != null) {
                startBackup(uri, password)
              } else {
                backupStatusTextView.text =
                  resources.getString(R.string.take_backup_view_backup_failed_missing_password)
                e("TakeBackup", "backup password missing.")
              }
            }
          }
        } else {
          d("TakeBackup", "onActivityResult returned because uri is null.")
        }
      } else {
        d("TakeBackup", "onActivityResult returned because data is null.")
      }
    } else {
      d("TakeBackup", "onActivityResult returned result code was not OK. resultCode=$resultCode")
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

  fun notifyBackupProgress(bc: BackupCreator) {
    backupStatusTextView.text = resources.getString(
      R.string.take_backup_view_backup_progress,
      bc.getProgress()
    )
    backupProgressIndicator.incrementProgressBy(
      (bc.getProgress() - backupProgressIndicator.progress).roundToInt()
    )
  }

  fun notifyBackupComplete(bc: BackupCreator) {
    backupProgressIndicator.visibility = View.INVISIBLE
    val error = bc.getError()
    // Null error means backup is successful
    if (error != null) {
      backupStatusTextView.text = error
    } else {
      backupStatusTextView.text = resources.getString(R.string.take_backup_view_backup_successful)
    }
    backupStartButton.isEnabled = true
    runningBackupCreator = null
  }

  private fun startBackup(treeUri: Uri, password: String) {
    val bc = BackupCreator(applicationContext, treeUri)
    bc.start(password)
    runningBackupCreator = bc
    applyBackupInProgressState(bc)
  }

  private fun applyBackupInProgressState(bc: BackupCreator) {
    bc.activity = this
    backupStartButton.isEnabled = false
    backupStatusTextView.visibility = View.VISIBLE
    backupProgressIndicator.visibility = View.VISIBLE
    if (bc.getProgress() == 100.0 && bc.getError() == null) {
      backupStatusTextView.text = resources.getString(R.string.take_backup_view_backup_successful)
      backupProgressIndicator.visibility = View.INVISIBLE
      runningBackupCreator = null
      backupStartButton.isEnabled = true
    } else if (bc.getProgress() > 0 && bc.getError() == null) {
      backupStatusTextView.text = resources.getString(
        R.string.take_backup_view_backup_progress,
        bc.getProgress()
      )
      backupProgressIndicator.progress = bc.getProgress().roundToInt()
    } else if (bc.getError() != null) {
      backupStatusTextView.text = bc.getError()
      backupProgressIndicator.visibility = View.INVISIBLE
      runningBackupCreator = null
      backupStartButton.isEnabled = true
    } else {
      backupStatusTextView.text = resources.getString(
        R.string.take_backup_view_backup_starting_backup_process
      )
      backupProgressIndicator.progress = 0
    }
  }
}
