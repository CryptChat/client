package cc.osama.cryptchat

import java.lang.Exception
import java.lang.StringBuilder

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

  fun formatNumber(code: String, number: String) : String {
    val format = map.values.toTypedArray().find {
      it.split("|")[0] == code.replace("+", "")
    }?.split("|")?.get(1) ?: return code + number
    if (format.replace(SPACE.toString(), "").length != number.length) {
      return code + number
    } else {
      val builder = StringBuilder()
      builder.append(code)
      builder.append(SPACE)
      var formatIndex = 0
      number.forEach { c ->
        if (format[formatIndex] == SPACE) {
          builder.append(SPACE)
          formatIndex++
        }
        builder.append(c)
        formatIndex++
      }
      return builder.toString()
    }
  }
}