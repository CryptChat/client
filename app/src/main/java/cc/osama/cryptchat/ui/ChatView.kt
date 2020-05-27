package cc.osama.cryptchat.ui

import android.os.Bundle
import androidx.recyclerview.widget.LinearLayoutManager
import cc.osama.cryptchat.R
import cc.osama.cryptchat.RecyclerViewImplementer
import cc.osama.cryptchat.db.Message
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
    dataset.add("MY String")
    dataset.add("Your String")
    chatBody.apply {
      setHasFixedSize(true)
      layoutManager = viewManager
      adapter = viewAdapter
    }
    chatMessageSend.setOnClickListener {
      if (chatMessageInput.text.isNotEmpty()) {
        dataset.add(chatMessageInput.text.toString())
        viewAdapter.notifyItemInserted(dataset.size)
      }
    }
  }

  override fun onClick(position: Int) {
  }

  override fun onBindViewHolder(holder: Adapter.ListItemHolder, position: Int) {
    holder.view.myRandomTextHolder.text = dataset[position]
  }
}
