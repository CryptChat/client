package cc.osama.cryptchat

import android.os.AsyncTask
import androidx.room.Database
import androidx.room.RoomDatabase
import cc.osama.cryptchat.db.EphemeralKey
import cc.osama.cryptchat.db.Server
import java.util.concurrent.Executor

@Database(
  entities = [
    Server::class,
    EphemeralKey::class
  ],
  version = 1
)
abstract class Database : RoomDatabase() {
  abstract fun servers() : Server.DataAccessObject
  abstract fun ephemeralKeys() : EphemeralKey.DataAccessObject
  class Executor(
    private val task: (executor: Executor) -> Any?,
    private val onProgress: (values: List<Any?>) -> Any?,
    private val after: (Any?) -> Any?
  ) : AsyncTask<Any?, Any?, Any?>() {
    override fun doInBackground(vararg params: Any?): Any? {
      return task(this)
    }

    override fun onProgressUpdate(vararg values: Any?) {
      super.onProgressUpdate(*values)
      val list = mutableListOf<Any?>()
      for (i in values) {
        list.add(i)
      }
      onProgress(list)
    }

    override fun onPostExecute(result: Any?) {
      super.onPostExecute(result)
      after(result)
    }

    fun customPublishProgress(value: String) {
      publishProgress(value)
    }
  }

  fun asyncExec(
    task: (executor: Executor) -> Any?,
    onProgress: (values: List<Any?>) -> Any? = {},
    after: (Any?) -> Any? = {}
  ) {
    Executor(task, onProgress, after).execute()
  }
}