package cc.osama.cryptchat

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.core.content.edit
import androidx.room.Room
import java.lang.Exception

class Cryptchat : Application() {
  class DatabaseReadonly : Exception()
  companion object {
    const val MESSAGES_CHANNEL_ID = "CRYPTCHAT_MESSAGES_CHANNEL"
    private var DB_INSTANCE: Database? = null
    private lateinit var sharedPrefs: SharedPreferences
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
      return if (this::sharedPrefs.isInitialized) {
        sharedPrefs
      } else {
        val name = context.packageName + "-shared-preferences"
        val pref = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        sharedPrefs = pref
        sharedPrefs
      }
    }
  }

  override fun onCreate() {
    super.onCreate()
    createNotificationChannels()
    // SyncMessagesWorker.enqueue(1, applicationContext)

    // registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
    //   override fun onActivityPaused(activity: Activity) {
    //     Log.w("SSSSSSSSSSSSSSS", activity.javaClass.name + " Paused!")
    //   }

    //   override fun onActivityStarted(activity: Activity) {
    //     Log.w("SSSSSSSSSSSSSSS", activity.javaClass.name + " Started!")
    //   }

    //   override fun onActivityDestroyed(activity: Activity) {
    //     Log.w("SSSSSSSSSSSSSSS", activity.javaClass.name + " Destroyed!")
    //   }

    //   override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
    //     Log.w("SSSSSSSSSSSSSSS", activity.javaClass.name + " blah!")
    //   }

    //   override fun onActivityStopped(activity: Activity) {
    //     Log.w("SSSSSSSSSSSSSSS", activity.javaClass.name + " Stopped!")
    //   }

    //   override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
    //     Log.w("SSSSSSSSSSSSSSS", activity.javaClass.name + " Created!")
    //   }

    //   override fun onActivityResumed(activity: Activity) {
    //     Log.w("SSSSSSSSSSSSSSS", activity.javaClass.name + " Resumed!")
    //   }
    // })
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