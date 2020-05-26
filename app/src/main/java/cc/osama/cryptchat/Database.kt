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
    private val task: (executor: Executor) -> Unit,
    private val after: () -> Unit
  ) : AsyncTask<Unit, Any?, Unit>() {
    override fun doInBackground(vararg params: Unit) {
      task(this)
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

    override fun onPostExecute(result: Unit) {
      super.onPostExecute(result)
      after()
    }

    fun execOnUIThread(callback: () -> Unit) {
      publishProgress("customPublishProgress", callback)
    }
  }

  fun asyncExec(
    task: (executor: Executor) -> Unit,
    after: () -> Unit = {}
  ) {
    Executor(task, after).execute()
  }
}