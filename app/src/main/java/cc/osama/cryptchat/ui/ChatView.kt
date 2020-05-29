package cc.osama.cryptchat.ui

import android.os.Bundle
import androidx.recyclerview.widget.LinearLayoutManager
import cc.osama.cryptchat.*
import cc.osama.cryptchat.db.Server
import cc.osama.cryptchat.db.User
import kotlinx.android.synthetic.main.activity_chat_view.*
import kotlinx.android.synthetic.main.chat_message.view.*

class ChatView : RecyclerViewImplementer<String>() {
  override val dataset = ArrayList<String>()
  override val layout = R.layout.chat_message
  override val viewAdapter = Adapter(dataset, layout, this)
  override val viewManager = LinearLayoutManager(this).also { it.stackFromEnd = true }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_chat_view)
    chatBody.apply {
      setHasFixedSize(true)
      layoutManager = viewManager
      adapter = viewAdapter
    }
    val user = intent?.extras?.get("user") as? User
    val server = intent?.extras?.get("server") as? Server
    chatMessageSend.setOnClickListener {
      if (chatMessageInput.text.isNotEmpty() && user != null && server != null) {
        val plaintext = chatMessageInput.text.toString()
        val handler = OutgoingMessageHandler(
          plaintext = plaintext,
          server = server,
          user = user,
          context = applicationContext
        )
        handler.saveToDb {
          dataset.add(plaintext)
          viewAdapter.notifyItemInserted(dataset.size)
        }
        handler.encryptAndSend()
      }
    }
  }

  override fun onBindViewHolder(holder: Adapter.ListItemHolder, position: Int) {
    holder.view.myRandomTextHolder.text = dataset[position]
  }
}
