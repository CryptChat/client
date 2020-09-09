package cc.osama.cryptchat.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.provider.MediaStore
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import cc.osama.cryptchat.*
import cc.osama.cryptchat.db.Server
import kotlinx.android.synthetic.main.activity_server_settings.*
import org.json.JSONObject
import java.io.ByteArrayOutputStream


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
    super.onActivityResult(requestCode, resultCode, data)
    val uri = data?.data
    if (requestCode == PICK_IMAGE && uri != null) {
      AsyncExec.run { asyncTask ->
        contentResolver.openInputStream(uri)?.use { input ->
          val tempPath = "_AVATAR_PICK_${CryptchatUtils.secureRandomHex(16)}"
          try {
            openFileOutput(tempPath, Context.MODE_PRIVATE).use { output ->
              val bytes = ByteArray(1024)
              var bytesRead = input.read(bytes)
              while (bytesRead != -1) {
                output.write(bytes, 0, bytesRead)
                bytesRead = input.read(bytes)
              }
            }
            val store = AvatarsStore(server.id, null, applicationContext)
            store.process(getFileStreamPath(tempPath), resources)
            val bigBitmap = store.bitmap(AvatarsStore.Sizes.Big)
            val smallBitmap = store.bitmap(AvatarsStore.Sizes.Small)
            if (bigBitmap != null && smallBitmap != null) {
              val stream = ByteArrayOutputStream()
              val successfulCompression = bigBitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
              stream.close()
              asyncTask.execMainThread {
                if (successfulCompression) {
                  uploadAvatar(stream, smallBitmap)
                } else {
                  AlertDialog.Builder(this).apply {
                    setNegativeButton(R.string.dialog_ok) { _, _ ->  }
                    setMessage(R.string.somehow_failed_to_compress_avatar)
                    create().show()
                  }
                }
              }
            } else {
              asyncTask.execMainThread {
                AlertDialog.Builder(this).apply {
                  setNegativeButton(R.string.dialog_ok) { _, _ ->  }
                  setMessage("Weird condition occurred where bigBitmap or smallBitmap are null")
                  create().show()
                }
              }
            }
          } finally {
            getFileStreamPath(tempPath).delete()
          }
        }
      }
    }
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
        CryptchatServer(applicationContext, server).request(
          method = CryptchatRequest.Methods.PUT,
          path = "/users.json",
          param = JSONObject().apply {
            put("name", server.userName)
          },
          success = {
            onUiThread {
              AlertDialog.Builder(this@ServerSettings).apply {
                setMessage(resources.getString(R.string.server_settings_info_updated_successfully))
                setNegativeButton(R.string.dialog_ok) { _, _ ->  }
                create().show()
              }
            }
          },
          failure = {
            onUiThread {
              val message = if (it.serverMessages.isNotEmpty()) {
                it.serverMessages.joinToString("\n")
              } else {
                it.toString()
              }
              AlertDialog.Builder(this@ServerSettings).apply {
                setMessage(message)
                setNegativeButton(R.string.dialog_ok) { _, _ ->  }
                create().show()
              }
            }
          },
          always = {
            onUiThread {
              saveChangesButton.isEnabled = true
              serverNameInput.isEnabled = true
              userNameInput.isEnabled = true
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
        val bitmap = AvatarsStore(server.id, null, applicationContext).bitmap(AvatarsStore.Sizes.Small)
        if (bitmap != null) {
          avatarHolder.setImageBitmap(bitmap)
        }
      }
    }
    if (Cryptchat.isReadonly(applicationContext)) {
      saveChangesButton.isEnabled = false
      serverNameInput.isEnabled = false
      userNameInput.isEnabled = false
      changeAvatarButton.isEnabled = false
      disabledSettingsNotice.visibility = View.VISIBLE
      saveChangesButton.visibility = View.INVISIBLE
    } else {
      saveChangesButton.isEnabled = true
      serverNameInput.isEnabled = true
      userNameInput.isEnabled = true
      changeAvatarButton.isEnabled = true
      disabledSettingsNotice.visibility = View.INVISIBLE
      saveChangesButton.visibility = View.VISIBLE
    }
  }

  override fun onOptionsItemSelected(item: MenuItem): Boolean {
    return if (item.itemId == android.R.id.home) {
      onBackPressed()
      true
    } else {
      super.onOptionsItemSelected(item)
    }
  }

  private fun uploadAvatar(stream: ByteArrayOutputStream, smallBitmap: Bitmap) {
    avatarUploadProgressBar.visibility = View.VISIBLE
    changeAvatarButton.visibility = View.GONE
    CryptchatServer(applicationContext, server).upload(
      path = "/avatar.json",
      file = stream.toByteArray(),
      fileContentType = "image/jpeg",
      success = {
        onUiThread {
          avatarHolder.setImageBitmap(smallBitmap)
          uploadSuccessfulIcon.visibility = View.VISIBLE
          handler.postDelayed({
            uploadSuccessfulIcon.visibility = View.GONE
          }, 1500)
        }
      },
      failure = {
        onUiThread {
          AlertDialog.Builder(this@ServerSettings).also { builder ->
            builder.setNegativeButton(R.string.dialog_ok) { _, _ ->  }
            if (it.serverMessages.isNotEmpty()) {
              builder.setMessage(it.serverMessages.joinToString("\n"))
            } else {
              builder.setMessage(resources.getString(R.string.server_responded_with_error, it.statusCode ?: -1))
            }
            builder.create().show()
          }
        }
      },
      always = {
        onUiThread {
          avatarUploadProgressBar.visibility = View.GONE
          changeAvatarButton.visibility = View.VISIBLE
        }
      }
    )
  }
}
