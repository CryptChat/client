package cc.osama.cryptchat.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.provider.MediaStore
import android.util.Log.w
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import cc.osama.cryptchat.*
import cc.osama.cryptchat.db.Server
import kotlinx.android.synthetic.main.activity_server_settings.*
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.URL


class ServerSettings : AppCompatActivity() {
  companion object {
    private const val PICK_IMAGE = 444
    fun createIntent(serverId: Long, context: Context) : Intent {
      return Intent(context, ServerSettings::class.java).also { intent ->
        intent.putExtra("serverId", serverId)
      }
    }
  }

  private val handler = Handler()
  private var serverId: Long = -1
  private lateinit var server: Server

  override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    val uri = data?.data
    if (requestCode == PICK_IMAGE && uri != null) {
      contentResolver.openFileDescriptor(uri, "r")?.fileDescriptor?.also { fileDescriptor ->
        BitmapFactory.Options().apply {
          inJustDecodeBounds = true
          BitmapFactory.decodeFileDescriptor(fileDescriptor, null, this)
          inSampleSize = CryptchatUtils.calcSampleSize(this, 700, 700)
          inJustDecodeBounds = false
          BitmapFactory.decodeFileDescriptor(fileDescriptor, null, this).also { bitmap ->
            val stream = ByteArrayOutputStream()
            val successfulCompression = bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
            stream.close()
            if (successfulCompression) {
              avatarUploadProgressBar.visibility = View.VISIBLE
              changeAvatarButton.visibility = View.GONE
              CryptchatServer(applicationContext, server).upload(
                path = "/avatar.json",
                file = stream.toByteArray(),
                fileContentType = "image/jpeg",
                success = {
                  avatarHolder.setImageBitmap(bitmap)
                  uploadSuccessfulIcon.visibility = View.VISIBLE
                  handler.postDelayed({
                    uploadSuccessfulIcon.visibility = View.GONE
                  }, 1500)
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
                always = { _, _, _ ->
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
          }
        }
      }
    }
    super.onActivityResult(requestCode, resultCode, data)
  }

  override fun onStop() {
    super.onStop()
    handler.removeCallbacksAndMessages(null)
    avatarUploadProgressBar.visibility = View.GONE
    changeAvatarButton.visibility = View.VISIBLE
    uploadSuccessfulIcon.visibility = View.GONE
  }

  override fun onDestroy() {
    super.onDestroy()
    handler.removeCallbacksAndMessages(null)
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    serverId = intent?.extras?.getLong("serverId") as Long
    setContentView(R.layout.activity_server_settings)
    setSupportActionBar(toolbar)
    supportActionBar?.setDisplayHomeAsUpEnabled(true)

    changeAvatarButton.setOnClickListener {
      Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI).also { intent ->
        intent.type = "image/*"
        startActivityForResult(Intent.createChooser(intent, null), PICK_IMAGE)
      }
    }
    saveChangesButton.setOnClickListener {
      var somethingChanged = false
      val newServerName = serverNameInput.text.toString()
      if (newServerName != server.name) {
        server.name = newServerName
        supportActionBar?.title = resources.getString(R.string.settings_activity_title, newServerName)
        somethingChanged = true
      }
      if (userNameInput.text.toString().trim() != server.userName) {
        saveChangesButton.isEnabled = false
        serverNameInput.isEnabled = false
        userNameInput.isEnabled = false
        server.userName = userNameInput.text.toString().trim()
        somethingChanged = true
        CryptchatServer(applicationContext, server).put(
          path = "/users.json",
          param = JSONObject().apply {
            put("name", server.userName)
          },
          always = { isSuccessful, _, error ->
            saveChangesButton.isEnabled = true
            serverNameInput.isEnabled = true
            userNameInput.isEnabled = true
            val message = if (isSuccessful) {
              resources.getString(R.string.server_settings_info_updated_successfully)
            } else if (error != null) {
              if (error.serverMessages.size > 0) {
                error.serverMessages.joinToString("\n")
              } else {
                resources.getString(R.string.server_responded_with_error, error.statusCode ?: -1)
              }
            } else {
              "Unexpected condition has occurred."
            }
            AlertDialog.Builder(this).apply {
              setMessage(message)
              setNegativeButton(R.string.dialog_ok) { _, _ ->  }
              create().show()
            }
          }
        )
      } else if (somethingChanged) {
        AlertDialog.Builder(this).apply {
          setMessage(resources.getString(R.string.server_settings_info_updated_successfully))
          setNegativeButton(R.string.dialog_ok) { _, _ ->  }
          create().show()
        }
      }
      if (somethingChanged) {
        AsyncExec.run {
          Cryptchat.db(applicationContext).also { db ->
            db.servers().update(server)
          }
        }
      }
    }
    AsyncExec.run {
      Cryptchat.db(applicationContext).also { db ->
        server = db.servers().findById(serverId) as Server
      }
    }
  }

  override fun onStart() {
    super.onStart()
    AsyncExec.run {
      server = Cryptchat.db(applicationContext).servers().findById(serverId) as Server
      val serverName = server.name
      it.execMainThread {
        serverNameInput.setText(server.name ?: "")
        userNameInput.setText(server.userName ?: "")
        supportActionBar?.title = if (serverName != null) {
          resources.getString(R.string.settings_activity_title, serverName)
        } else {
          resources.getString(R.string.settings_activity_title_without_server_name)
        }
      }
    }
    saveChangesButton.isEnabled = true
    serverNameInput.isEnabled = true
    userNameInput.isEnabled = true
  }

  override fun onOptionsItemSelected(item: MenuItem): Boolean {
    return if (item.itemId == android.R.id.home) {
      onBackPressed()
      true
    } else {
      super.onOptionsItemSelected(item)
    }
  }

  fun downloadImage() {
    AsyncExec.run {
      val url = URL("http://10.0.2.2:3000/avatar/4649ff268589e29ba929bb09679df202a5c80571")
      val connection = url.openConnection()
      Environment.getExternalStorageDirectory()
      connection.doInput = true
      connection.connect()
      val input = connection.getInputStream()
      val bitmap = BitmapFactory.decodeStream(input)
      it.execMainThread {
        avatarHolder.setImageBitmap(bitmap)
      }
    }
  }
}
