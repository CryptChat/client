package cc.osama.cryptchat.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.Handler
import android.provider.MediaStore
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import cc.osama.cryptchat.CryptchatServer
import cc.osama.cryptchat.CryptchatUtils
import cc.osama.cryptchat.R
import cc.osama.cryptchat.db.Server
import kotlinx.android.synthetic.main.activity_server_settings.*
import java.io.ByteArrayOutputStream


class ServerSettings : AppCompatActivity() {
  companion object {
    private const val PICK_IMAGE = 444
    fun createIntent(server: Server, context: Context) : Intent {
      return Intent(context, ServerSettings::class.java).also { intent ->
        intent.putExtra("server", server)
      }
    }
  }

  private val handler = Handler()
  override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    val uri = data?.data
    if (requestCode == PICK_IMAGE && uri != null) {
      contentResolver.openFileDescriptor(uri, "r")?.fileDescriptor?.also { fileDescriptor ->
        BitmapFactory.Options().apply {
          inJustDecodeBounds = true
          BitmapFactory.decodeFileDescriptor(fileDescriptor, null, this)
          inSampleSize = CryptchatUtils.calcSampleSize(this, 700, 700)
          inJustDecodeBounds = false
          BitmapFactory.decodeFileDescriptor(fileDescriptor, null, this).also view@ {
            var bitmap = it
            val stream = ByteArrayOutputStream()
            val successfulCompression = bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
            stream.close()
            if (successfulCompression) {
              val server  = intent?.extras?.get("server") as Server
              avatarUploadProgressBar.visibility = View.VISIBLE
              changeAvatarButton.visibility = View.GONE
              CryptchatServer(applicationContext, server).upload(
                path = "/avatar.json",
                file = stream.toByteArray(),
                fileContentType = "image/jpeg",
                success = {
                  uploadSuccessfulIcon.visibility = View.VISIBLE
                  handler.postDelayed({
                    uploadSuccessfulIcon.visibility = View.GONE
                  }, 1000)
                },
                failure = {
                  AlertDialog.Builder(this@ServerSettings).also { builder ->
                    builder.setNegativeButton(R.string.dialog_ok) { _, _ ->  }
                    if (it.serverMessages.size > 0) {
                      builder.setMessage(it.serverMessages.joinToString("\n"))
                    } else {
                      builder.setMessage(resources.getString(R.string.server_responded_with_error, it.statusCode ?: -1))
                    }
                    builder.create().show()
                  }
                },
                always = {
                  avatarUploadProgressBar.visibility = View.GONE
                  changeAvatarButton.visibility = View.VISIBLE
                }
              )
            } else {
              AlertDialog.Builder(this@ServerSettings).apply {
                setNegativeButton(R.string.dialog_ok) { _, _ ->  }
                setMessage(R.string.somehow_failed_to_compress_avatar)
                create().show()
              }
            }
            avatarHolder.setImageBitmap(bitmap)
          }
        }
      }
    }
    super.onActivityResult(requestCode, resultCode, data)
  }

  override fun onStart() {
    super.onStart()
    avatarUploadProgressBar.visibility = View.GONE
    changeAvatarButton.visibility = View.VISIBLE
    uploadSuccessfulIcon.visibility = View.GONE
  }

  override fun onStop() {
    super.onStop()
    handler.removeCallbacksAndMessages(null)
  }

  override fun onDestroy() {
    super.onDestroy()
    handler.removeCallbacksAndMessages(null)
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_server_settings)
    val server = intent?.extras?.get("server") as Server
    serverNameInput.setText(server.name ?: "")

    changeAvatarButton.setOnClickListener {
      Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI).also { intent ->
        intent.type = "image/*"
        startActivityForResult(Intent.createChooser(intent, null), PICK_IMAGE)
      }
    }
    setSupportActionBar(toolbar)
    val serverName = server.name
    if (serverName != null) {
      supportActionBar?.title = resources.getString(R.string.settings_activity_title, serverName.capitalize())
    } else {
      supportActionBar?.title = resources.getString(R.string.settings_activity_title_without_server_name)
    }
  }
}
