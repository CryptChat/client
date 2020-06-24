package cc.osama.cryptchat

import android.content.res.Resources
import android.graphics.BitmapFactory
import android.util.TypedValue
import java.lang.IllegalArgumentException
import java.lang.StringBuilder
import java.security.SecureRandom

class CryptchatUtils {
  companion object {
    fun toLong(value: Any?): Long? {
      return (value as? Int)?.toLong() ?: (value as? Long)
    }

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
  }
}