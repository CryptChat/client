package cc.osama.cryptchat

import android.content.res.Resources
import android.graphics.BitmapFactory
import android.util.TypedValue
import org.json.JSONObject
import java.lang.IllegalArgumentException
import java.lang.StringBuilder
import java.security.SecureRandom
import kotlin.experimental.and

class CryptchatUtils {
  companion object {
    fun dp2px(dp: Int, r: Resources) : Float {
      return TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        dp.toFloat(),
        r.displayMetrics
      )
    }

    // credits to https://developer.android.com/topic/performance/graphics/load-bitmap#load-bitmap
    fun calcSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int) : Int {
      val (height: Int, width: Int) = options.run { outHeight to outWidth }
      var inSampleSize = 1

      if (height > reqHeight || width > reqWidth) {
        val halfHeight: Int = height / 2
        val halfWidth: Int = width / 2

        while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
          inSampleSize *= 2
        }
      }

      return inSampleSize
    }

    fun secureRandomHex(bytes: Int) : String {
      if (bytes < 1) {
        throw IllegalArgumentException("bytes must be larger than or equal to 1, got $bytes")
      }
      val builder = StringBuilder()
      for (i in 0 until bytes) {
        builder.append(String.format("%02x", SecureRandom().nextInt(255)))
      }
      return builder.toString()
    }

    fun jsonOptString(json: JSONObject, key: String) : String? {
      return if (json.isNull(key)) {
        null
      } else {
        json.optString(key)
      }
    }

    fun bytesToBits(bytes: ByteArray) : BooleanArray {
      val bits = BooleanArray(bytes.size * 8)
      for (i in bytes.indices) {
        val byte = bytes[i].toInt()
        for (j in 0 until 8) {
          bits[i * 8 + j] = (byte and (1 shl (7 - j))) != 0
        }
      }
      return bits
    }
  }
}