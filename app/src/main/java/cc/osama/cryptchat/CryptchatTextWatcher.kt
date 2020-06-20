package cc.osama.cryptchat

import android.text.Editable
import android.text.TextWatcher

class CryptchatTextWatcher(
  private val after: ((Editable?) -> Unit)? = null,
  private val before: ((CharSequence?, Int, Int, Int) -> Unit)? = null,
  private val on: ((CharSequence?, Int, Int, Int) -> Unit)? = null
) : TextWatcher {
  override fun afterTextChanged(s: Editable?) {
    after?.invoke(s)
  }
  override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
    before?.invoke(s, start, count, after)
  }
  override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
    on?.invoke(s, start, before, count)
  }
}
