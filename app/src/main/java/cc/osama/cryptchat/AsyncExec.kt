package cc.osama.cryptchat

import android.os.AsyncTask

class AsyncExec<Result>(
  private val task: (AsyncExec<Result>) -> Result,
  private val after: (Result) -> Unit
) : AsyncTask<Unit, () -> Unit, Result>() {
  companion object {
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