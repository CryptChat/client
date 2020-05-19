package cc.osama.cryptchat

import android.os.AsyncTask
import androidx.room.Database
import androidx.room.RoomDatabase
import cc.osama.cryptchat.db.Server

@Database(
  entities = [Server::class],
  version = 1
)
abstract class Database : RoomDatabase() {
  abstract fun server() : Server.DataAccessObject

  fun asyncExec(task: () -> Unit, after: () -> Unit = {}) {
    class Executor : AsyncTask<Unit, Unit, Unit>() {
      override fun doInBackground(vararg params: Unit?) {
        task()
      }

      override fun onPostExecute(result: Unit?) {
        super.onPostExecute(result)
        after()
      }
    }
    Executor().execute()
  }
}