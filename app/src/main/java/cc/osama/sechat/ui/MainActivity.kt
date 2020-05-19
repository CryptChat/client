package cc.osama.sechat.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log.d
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import cc.osama.sechat.R
import cc.osama.sechat.Sechat
import cc.osama.sechat.SechatServer
import kotlinx.android.synthetic.main.activity_main.*
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.net.URL
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.Security
import java.security.spec.*


class MainActivity : AppCompatActivity() {
  private val messages = ArrayList<String>()
  private val viewAdapter =
    MessagesAdapter(messages, this)
  private val viewManager = LinearLayoutManager(this).also {
    it.stackFromEnd = true
  }

  class MessagesAdapter(private val dataset: ArrayList<String>, private val context: Context): RecyclerView.Adapter<MessagesAdapter.MessageHolder>() {
    class MessageHolder(val view: TextView) : RecyclerView.ViewHolder(view)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageHolder {
      return MessageHolder(
        TextView(context)
      )
    }

    override fun getItemCount(): Int = dataset.size

    override fun onBindViewHolder(holder: MessageHolder, position: Int) {
      holder.view.text = dataset[position]
    }
  }

  override fun onDestroy() {
    super.onDestroy()
    Sechat.currentChatView = null
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    // startActivity(Intent(this, EnterServerAddress::class.java))
    Sechat.currentChatView = this
    setContentView(R.layout.activity_main)
    val kpg = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC)
    // kpg.initialize()
    val kp = kpg.genKeyPair()

    // d("SECURITY", kp.private.encoded.size.toString())
    // d("SECURITY", String(Base64.encode(kp.public.encoded, Base64.DEFAULT)))
    // d("SECURITY", String(kp.public.encoded).length.toString())
    // d("SECURITY", "----------------------")
    // d("SECURITY", String(Base64.encode(kp.private.encoded, Base64.DEFAULT)))
    // d("SECURITY", String(kp.private.encoded).length.toString())A
    // val uri = URL("http2://www.osama.cc")
    // d("URL host", uri.host?.toString() ?: "")
    // d("URL path", uri.path?.toString() ?: "")
    // d("URL authority", uri.authority?.toString() ?: "")
    // d("URL query", uri.query?.toString() ?: "")
    messagesContainer.apply {
      setHasFixedSize(true)
      layoutManager = viewManager
      adapter = viewAdapter
    }
    sendButton.setOnClickListener {
      startActivity(Intent(this, EnterServerAddress::class.java))
      return@setOnClickListener
      if (composer.text == null) return@setOnClickListener
      val params = JSONObject()
      val message = JSONObject()
      message.put("body", composer.text)
      params.put("message", message)
      addToMessagesAndNotify(composer.text.toString())
      SechatServer(applicationContext, "").post(
        "/message.json",
        params,
        failure = { error ->
          var msg = ""
          if (error?.networkResponse?.data != null) {
            val json = JSONObject(String(error.networkResponse.data))
            val jsonArray = json["messages"] as JSONArray
            for (i in 0 until jsonArray.length()) {
              msg += "\t${jsonArray[i]}"
            }
          } else {
            msg = "Unknown error occurred"
          }
          addToMessagesAndNotify(msg)
        }
      )
      composer.text.clear()
    }
  }

  fun addToMessagesAndNotify(newItem: String) {
    messages.add(newItem)
    viewAdapter.notifyItemInserted(messages.size)
  }
}
