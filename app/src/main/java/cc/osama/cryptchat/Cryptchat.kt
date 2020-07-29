package cc.osama.cryptchat

import android.app.Activity
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.Log.w
import androidx.room.Room
import cc.osama.cryptchat.ui.ChatView
import cc.osama.cryptchat.ui.MainActivity
import cc.osama.cryptchat.worker.InstanceIdsManagerWorker
import cc.osama.cryptchat.worker.SyncMessagesWorker

class Cryptchat : Application() {
  companion object {
    const val MESSAGES_CHANNEL_ID = "CRYPTCHAT_MESSAGES_CHANNEL"
    private var DB_INSTANCE: Database? = null
    fun db(context: Context) =
      DB_INSTANCE ?: synchronized(this) {
        DB_INSTANCE ?: Room.databaseBuilder(
          context,
          Database::class.java,
          "cryptchat-database"
        ).build().also { DB_INSTANCE = it }
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