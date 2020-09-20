package cc.osama.cryptchat

import kotlin.collections.HashMap

class HttpHeaders : HashMap<String, String>() {
  override fun get(key: String): String? {
    return super.get(key.toLowerCase())
  }

  override fun put(key: String, value: String): String? {
    return super.put(key.toLowerCase(), value)
  }

  override fun putAll(from: Map<out String, String>) {
    val lowerCaseFrom = HashMap<String, String>()
    from.forEach {
      lowerCaseFrom[it.key.toLowerCase()] = it.value
    }
    super.putAll(lowerCaseFrom.toMap())
  }
}