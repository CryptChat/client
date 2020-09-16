package cc.osama.cryptchat.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.View
import cc.osama.cryptchat.BackupCreator
import cc.osama.cryptchat.Cryptchat
import cc.osama.cryptchat.R
import kotlinx.android.synthetic.main.activity_take_backup.*
import kotlin.math.roundToInt

class TakeBackup : AppCompatActivity() {
  companion object {
    private const val BACKUP_TREE_REQUEST_CODE = 395
    private var backupInProgress: BackupCreator? = null
    fun createIntent(context: Context) = Intent(context, TakeBackup::class.java)
    fun isBackupInProgress() = backupInProgress != null
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_take_backup)
    setSupportActionBar(takeBackupToolbar)
    supportActionBar?.setDisplayHomeAsUpEnabled(true)
    supportActionBar?.title = resources.getString(R.string.take_backup_view_toolbar_title)
    backupStartButton.setOnClickListener {
      val treeUri = Cryptchat.backupsTreeUri(applicationContext)
      if (treeUri != null) {
        startBackup(treeUri)
      } else {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        startActivityForResult(intent, BACKUP_TREE_REQUEST_CODE)
      }
    }
  }

  override fun onStart() {
    super.onStart()
    val bc = backupInProgress
    if (bc != null) {
      applyBackupInProgressState(bc)
    }
  }

  override fun onPause() {
    super.onPause()
    backupInProgress?.activity = null
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
              startBackup(uri)
            }
          }
        } else {
          Log.d("TakeBackup", "onActivityResult returned because uri is null.")
        }
      } else {
        Log.d("TakeBackup", "onActivityResult returned because data is null.")
      }
    } else {
      Log.d("TakeBackup", "onActivityResult returned result code was not OK. resultCode=$resultCode")
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
    backupInProgress = null
  }

  private fun startBackup(treeUri: Uri) {
    val bc = BackupCreator(applicationContext, treeUri)
    bc.start()
    backupInProgress = bc
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
      backupInProgress = null
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
      backupInProgress = null
      backupStartButton.isEnabled = true
    } else {
      backupStatusTextView.text = resources.getString(
        R.string.take_backup_view_backup_starting_backup_process
      )
      backupProgressIndicator.progress = 0
    }
  }
}
