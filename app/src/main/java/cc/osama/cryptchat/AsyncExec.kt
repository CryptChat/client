package cc.osama.cryptchat

import android.os.AsyncTask
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper

class AsyncExec<Result>(
  private val task: (AsyncExec<Result>) -> Result,
  private val after: (Result) -> Unit
) : AsyncTask<Unit, () -> Unit, Result>() {
  companion object {
    private class DedicatedThread(name: String) {
      private val handler = Handler(HandlerThread(name).apply { start() }.looper)
      fun post(cb: () -> Unit) {
        handler.post(cb)
      }
    }

    enum class Threads { Db, Network }
    private val dbThread = DedicatedThread("Database")
    private val networkThread = DedicatedThread("Network")
    private val mainThread = Handler(Looper.getMainLooper())

    fun <Result> run(
      task: (AsyncExec<Result>) -> Result,
      after: (Result) -> Unit = {}
    ) {
      AsyncExec(task, after).execute()
    }

    fun run(
      task: (AsyncExec<Unit>) -> Unit
    ) {
      run(task = task, after = {})
    }

    fun run(t: Threads, cb: () -> Unit) {
      when(t) {
        Threads.Db -> {
          dbThread.post(cb)
        }
        Threads.Network -> {
          networkThread.post(cb)
        }
      }
    }

    fun onUiThread(cb: () -> Unit) {
      mainThread.post(cb)
    }
  }

  override fun doInBackground(vararg params: Unit) : Result {
    return task(this)
  }

  override fun onProgressUpdate(vararg values: () -> Unit) {
    super.onProgressUpdate(*values)
    for (i in values) {
      i.invoke()
    }
  }

  fun execMainThread(callback: () -> Unit) {
    publishProgress(callback)
  }

  override fun onPostExecute(result: Result) {
    super.onPostExecute(result)
    after(result)
  }
}