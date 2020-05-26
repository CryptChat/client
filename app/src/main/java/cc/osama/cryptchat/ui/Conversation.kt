package cc.osama.cryptchat.ui

import android.os.Bundle
import android.util.Log.w
import cc.osama.cryptchat.R
import cc.osama.cryptchat.RecyclerViewImplementer
import kotlinx.android.synthetic.main.activity_conversation.*
import kotlinx.android.synthetic.main.message.view.*

class Conversation : RecyclerViewImplementer<String>() {
  override val dataset = ArrayList<String>()
  override val layout = R.layout.message
  override val viewAdapter = Adapter(dataset, layout, this)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_conversation)
    viewManager.stackFromEnd = true
    dataset.add("Hello")
    dataset.add("Welcome")
    messagesHolder.apply {
      setHasFixedSize(true)
      layoutManager = layoutManager
      adapter = viewAdapter
    }
    sendMessageButton.setOnClickListener {
      w("LOOOOOOOOOG", "SSSSSSSSSS")
      if (messageInput.text.isNotEmpty()) {
        w("LOOOOOOOOOG", messageInput.text.toString())
        dataset.add(messageInput.text.toString())
        viewAdapter.notifyDataSetChanged()
        w("LOOOOOOOOOG", viewAdapter.itemCount.toString())
        viewAdapter.notifyItemInserted(dataset.size)
      }
    }
  }

  override fun onClick(position: Int) {
  }

  override fun onBindViewHolder(holder: Adapter.ListItemHolder, position: Int) {
    w("LOOOOOOOOOG", "UUUUUUUUUUUUUUUU")
    holder.view.messageHolder.text = dataset[position]
  }
}
