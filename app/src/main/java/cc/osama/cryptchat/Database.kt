package cc.osama.cryptchat

import android.os.AsyncTask
import androidx.room.Database
import androidx.room.RoomDatabase
import cc.osama.cryptchat.db.EphemeralKey
import cc.osama.cryptchat.db.Server
import cc.osama.cryptchat.db.User
import java.util.concurrent.Executor

@Database(
  entities = [
    Server::class,
    EphemeralKey::class,
    User::class
  ],
  version = 1
)
abstract class Database : RoomDatabase() {
  abstract fun servers() : Server.DataAccessObject
  abstract fun ephemeralKeys() : EphemeralKey.DataAccessObject
  abstract fun users() : User.DataAccessObject

  class Executor(
    private val task: (executor: Executor) -> Any?,
    private val after: (Any?) -> Any?
  ) : AsyncTask<Any?, Any?, Any?>() {
    override fun doInBackground(vararg params: Any?): Any? {
      return task(this)
    }

    override fun onProgressUpdate(vararg values: Any?) {
      super.onProgressUpdate(*values)
      if (values.size == 2 && (values[0] as? String) == "customPublishProgress") {
        val callback = values[1] as? () -> Unit
        if (callback != null) {
          callback()
        }
      }
    }

    override fun onPostExecute(result: Any?) {
      super.onPostExecute(result)
      after(result)
    }

    fun publishProgress(callback: () -> Unit) {
      publishProgress("customPublishProgress", callback)
    }
  }

  fun asyncExec(
    task: (executor: Executor) -> Any?,
    after: (Any?) -> Any? = {}
  ) {
    Executor(task, after).execute()
  }
}