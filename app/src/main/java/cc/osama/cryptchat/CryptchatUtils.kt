package cc.osama.cryptchat

class CryptchatUtils {
  companion object {
    fun toLong(value: Any?): Long? {
      return (value as? Int)?.toLong() ?: (value as? Long)
    }
  }
}