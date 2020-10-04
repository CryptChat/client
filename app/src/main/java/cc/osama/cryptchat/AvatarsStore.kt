package cc.osama.cryptchat

import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log.e
import android.util.Log.w
import java.io.File
import java.lang.Exception
import java.net.URL
import java.util.*

class AvatarsStore(
  private val serverId: Long,
  private val userId: Long?,
  private val context: Context
) {
  enum class Sizes {
    Big, // ~1000 px
    Small // 55 dp converted to px
  }
  companion object {
    private const val USERS_LIST_AVATARS_SIZE = 55 // dp
  }
  fun download(url: String, r: Resources) {
    val urlObj = URL(url)
    val connection = urlObj.openConnection()
    connection.doInput = true
    connection.connect()
    val tempPath = "_TEMP_AVATAR_${CryptchatUtils.secureRandomHex(16)}"
    try {
      connection.getInputStream().use { input ->
        context.openFileOutput(tempPath, Context.MODE_PRIVATE).use {
          val bytes = ByteArray(1024)
          var bytesRead = input.read(bytes)
          while (bytesRead != -1) {
            it.write(bytes, 0, bytesRead)
            bytesRead = input.read(bytes)
          }
        }
      }
      process(context.getFileStreamPath(tempPath), r)
    } catch (ex: Exception) {
      e("AvatarsStore", "avatar download failed", ex)
    } finally {
      val tempFile = context.getFileStreamPath(tempPath)
      if (tempFile.exists()) tempFile.delete()
    }
  }

  fun process(file: File, r: Resources) {
    BitmapFactory.Options().apply {
      inJustDecodeBounds = true
      BitmapFactory.decodeFile(file.absolutePath, this)
      inSampleSize = if (outWidth > 1000 || outWidth > 1000) {
        CryptchatUtils.calcSampleSize(this, 1000, 1000)
      } else {
        1
      }
      inJustDecodeBounds = false
      BitmapFactory.decodeFile(file.absolutePath, this).also { bitmap ->
        context.openFileOutput(pathForSize(Sizes.Big), Context.MODE_PRIVATE).use { stream ->
          bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
        }
        val scaledBitmap = Bitmap.createScaledBitmap(
          bitmap,
          CryptchatUtils.dp2px(USERS_LIST_AVATARS_SIZE, r).toInt(),
          CryptchatUtils.dp2px(USERS_LIST_AVATARS_SIZE, r).toInt(),
          true
        )
        context.openFileOutput(pathForSize(Sizes.Small), Context.MODE_PRIVATE).use { stream ->
          scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
        }
      }
    }
  }

  fun bitmap(size: Sizes) : Bitmap? {
    val file = context.getFileStreamPath(pathForSize(size))
    return if (!file.exists()) {
      null
    } else {
      BitmapFactory.decodeFile(file.absolutePath)
    }
  }

  fun delete() {
    for (size in Sizes.values()) {
      val file = context.getFileStreamPath(pathForSize(size))
      if (file.exists()) file.deleteOnExit()
    }
  }

  private fun pathForSize(size: Sizes) : String {
    var savePath = "avatar-$serverId"
    if (userId != null) {
      savePath += "-$userId"
    }
    savePath += "-"
    savePath += size.name.toLowerCase(Locale.ROOT)
    savePath += ".jpeg"
    return savePath
  }
}