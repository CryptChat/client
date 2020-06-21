package cc.osama.cryptchat.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log.e
import android.util.Log.w
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.marginLeft
import androidx.core.view.marginRight
import androidx.core.view.setMargins
import cc.osama.cryptchat.CryptchatTextWatcher
import cc.osama.cryptchat.R
import cc.osama.cryptchat.db.Server
import kotlinx.android.synthetic.main.activity_server_settings.*
import kotlinx.android.synthetic.main.edit_dialog.view.*

class ServerSettings : AppCompatActivity() {
  companion object {
    private const val PICK_IMAGE = 444
    fun createIntent(server: Server, context: Context) : Intent {
      return Intent(context, ServerSettings::class.java).also { intent ->
        intent.putExtra("server", server)
      }
    }
  }

  override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    if (requestCode == PICK_IMAGE && data != null) {
      avatarHolder.setImageURI(data.data)
      e("PICKERRR", "$data")
    }
    super.onActivityResult(requestCode, resultCode, data)
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_server_settings)
    val server = intent?.extras?.get("server") as Server
    nameInput.setText(server.name ?: "")

    changeAvatarButton.setOnClickListener {
      Intent().also { intent ->
        intent.type = "image/*"
        intent.action = Intent.ACTION_GET_CONTENT
        startActivityForResult(Intent.createChooser(intent, "Select avatar"), PICK_IMAGE)
      }
    }
    saveChangesButton.visibility = View.INVISIBLE
    nameInput.addTextChangedListener(CryptchatTextWatcher(
      on = { s, _, _, _ ->
        if (s != null && s.isNotEmpty() && s.trim().toString() != server.name) {
          saveChangesButton.visibility = View.VISIBLE
        } else {
          saveChangesButton.visibility = View.INVISIBLE
        }
      }
    ))
    // AlertDialog.Builder(this).also { dialog ->
    //   return@also
    //   val view = layoutInflater.inflate(R.layout.edit_dialog, null, false)
    //   view.dialogTitle.text = resources.getString(R.string.enter_your_name_alert_dialog)
    //   dialog.setView(view)
    //   dialog.setNegativeButton(R.string.dialog_cancel, null)
    //   dialog.setPositiveButton(R.string.dialog_save) { _, _ ->
    //     e("CLICKED SAVE", "111111")
    //   }
    //   dialog.create().show()
    // }
  }
}
