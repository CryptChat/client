package cc.osama.cryptchat

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import androidx.core.content.edit
import androidx.documentfile.provider.DocumentFile
import androidx.room.Room
import cc.osama.cryptchat.worker.SyncMessagesWorker
import cc.osama.cryptchat.worker.SyncUsersWorker

class Cryptchat : Application() {
  companion object {
    const val MESSAGES_CHANNEL_ID = "CRYPTCHAT_MESSAGES_CHANNEL"
    private var DB_INSTANCE: Database? = null
    private var sharedPrefs: SharedPreferences? = null
    fun db(context: Context): Database {
      return DB_INSTANCE ?: synchronized(this) {
        DB_INSTANCE ?: Room.databaseBuilder(
          context,
          Database::class.java,
          Database.Name
        ).build().also { DB_INSTANCE = it }
      }
    }

    fun isReadonly(context: Context) : Boolean {
      return sharedPreferences(context).getBoolean("readonly-mode", false)
    }

    fun enableReadonly(context: Context) {
      sharedPreferences(context).edit {
        putBoolean("readonly-mode", true)
        apply()
      }
    }

    fun disableReadonly(context: Context) {
      sharedPreferences(context).edit {
        remove("readonly-mode")
        apply()
      }
      DB_INSTANCE = null
    }

    fun sharedPreferences(context: Context) : SharedPreferences {
      return sharedPrefs ?: synchronized(this) {
        sharedPrefs ?: context.getSharedPreferences(
          context.packageName + "-shared-preferences",
          Context.MODE_PRIVATE
        ).also { sharedPrefs = it }
      }
    }

    fun backupsTreeUri(context: Context) : Uri? {
      val permissions = context.contentResolver.persistedUriPermissions
      return if (permissions.size > 0) {
        val uri = permissions[0].uri
        val documentTree = DocumentFile.fromTreeUri(context, uri)
        if (documentTree != null && documentTree.exists()) {
          uri
        } else {
          context.contentResolver.releasePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_WRITE_URI_PERMISSION
          )
          null
        }
      } else {
        null
      }
    }

    fun setBackupsDir(context: Context, newUri: Uri) {
      backupsTreeUri(context)?.also {
        context.contentResolver.releasePersistableUriPermission(
          it,
          Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
      }
      context.contentResolver.takePersistableUriPermission(
        newUri,
        Intent.FLAG_GRANT_WRITE_URI_PERMISSION
      )
    }

    fun defaultTheme(context: Context) : Int {
      val key = "dark_theme_default_theme"
      return if (sharedPreferences(context).getBoolean(key, false)) {
        R.style.AppTheme_Dark
      } else {
        R.style.AppTheme_Light
      }
    }

    fun setDarkThemeDefault(context: Context) {
      val key = "dark_theme_default_theme"
      sharedPreferences(context).edit {
        putBoolean(key, true)
        commit()
      }
    }

    fun resetDefaultTheme(context: Context) {
      val key = "dark_theme_default_theme"
      sharedPreferences(context).edit {
        remove(key)
        commit()
      }
    }
  }

  override fun onCreate() {
    super.onCreate()
    AsyncExec.run(AsyncExec.Companion.Threads.Db) {
      db(applicationContext).servers().getAll().forEach { server ->
        SyncMessagesWorker.enqueue(serverId = server.id, context = applicationContext)
        SyncUsersWorker.enqueue(serverId = server.id, context = applicationContext)
      }
    }
    createNotificationChannels()
  }

  private fun createNotificationChannels() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val name = resources.getString(R.string.notification_channel_messages)
      val desc = resources.getString(R.string.notification_channel_messages_desc)
      val importance = NotificationManager.IMPORTANCE_DEFAULT
      val channel = NotificationChannel(MESSAGES_CHANNEL_ID, name, importance).apply {
        description = desc
      }
      val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
      manager.createNotificationChannel(channel)
    }
  }
}