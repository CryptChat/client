package cc.osama.cryptchat.ui

import android.os.Bundle
import android.util.Log.w
import androidx.recyclerview.widget.LinearLayoutManager
import cc.osama.cryptchat.CryptchatServer
import cc.osama.cryptchat.R
import cc.osama.cryptchat.RecyclerViewImplementer
import cc.osama.cryptchat.db.Message
import cc.osama.cryptchat.db.Server
import cc.osama.cryptchat.db.User
import kotlinx.android.synthetic.main.activity_chat_view.*
import kotlinx.android.synthetic.main.chat_message.view.*
import org.json.JSONObject

class ChatView : RecyclerViewImplementer<String>() {
  override val dataset = ArrayList<String>()
  override val layout = R.layout.chat_message
  override val viewAdapter = Adapter(dataset, layout, this)
  override val viewManager = LinearLayoutManager(this).also { it.stackFromEnd = true }
  private val user = intent.extras?.get("user") as? User
  private val server = intent.extras?.get("server") as? Server

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_chat_view)
    chatBody.apply {
      setHasFixedSize(true)
      layoutManager = viewManager
      adapter = viewAdapter
    }
    chatMessageSend.setOnClickListener {
      if (chatMessageInput.text.isNotEmpty() && user != null && server != null) {
        CryptchatServer(applicationContext, server.address).post(
          path = "/ephemeral-keys/grab.json",
          param = JSONObject().also { it.put("user_id", user.idOnServer) },
          success = {
            val keyJson = it["ephemeral_key"] as? JSONObject
            if (keyJson != null) {
              val stringKey = keyJson["key"] as? String
              val idOnDevice = (keyJson["id_on_user_device"] as? Int)?.toLong() ?: keyJson["id_on_user_device"] as? Long
              if (stringKey != null && idOnDevice != null) {

              }
            }
          }
        )
        dataset.add(chatMessageInput.text.toString())
        viewAdapter.notifyItemInserted(dataset.size)
      }
    }
  }

  override fun onBindViewHolder(holder: Adapter.ListItemHolder, position: Int) {
    holder.view.myRandomTextHolder.text = dataset[position]
  }
}
