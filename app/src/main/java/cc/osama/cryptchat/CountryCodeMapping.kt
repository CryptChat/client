package cc.osama.cryptchat

import java.lang.Exception

object CountryCodeMapping {
  const val SPACE = ' '
  class NoSuchCountryException : Exception()
  private val map = mapOf(
    "JO" to "962|X XXXX XXXX",
    "KW" to "965|XXXX XXXX",
    "SA" to "966|XX XXX XXXX"
  )

  val countries = map.keys.toTypedArray().sortedArray()

  fun codeFor(country: String) : String {
    val value = map[country] ?: throw NoSuchCountryException()
    return "+${value.split('|')[0]}"
  }

  fun formatFor(country: String) : String {
    val value = map[country] ?: throw NoSuchCountryException()
    return value.split('|')[1]
  }
}