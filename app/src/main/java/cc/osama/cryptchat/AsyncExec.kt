package cc.osama.cryptchat

import android.os.AsyncTask

class AsyncExec<Params, Progress, Result>(
  private val task: () -> Result,
  private val after: (Result) -> Unit
) : AsyncTask<Params, Progress, Result>() {
  companion object {
    fun <Result> run(
      task: () -> Result,
      after: (Result) -> Unit = {}
    ) : AsyncTask<Unit, Unit, Result> {
      return AsyncExec<Unit, Unit, Result>(task, after).execute()
    }
  }

  override fun doInBackground(vararg params: Params) : Result {
    return task()
  }

  override fun onPostExecute(result: Result) {
    super.onPostExecute(result)
    after(result)
  }
}