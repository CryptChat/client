package cc.osama.cryptchat.ui

import android.os.Bundle
import androidx.recyclerview.widget.LinearLayoutManager
import cc.osama.cryptchat.R
import cc.osama.cryptchat.Cryptchat
import cc.osama.cryptchat.RecyclerViewImplementer
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.server_users_list_item.view.*
import org.json.JSONObject


class MainActivity : RecyclerViewImplementer<String>() {
  override val dataset = ArrayList<String>()
  override val defaultLayout = R.layout.server_users_list_item
  override val viewAdapter = Adapter(dataset, defaultLayout, this)
  override val viewManager = LinearLayoutManager(this).also {
    it.stackFromEnd = true
  }

  override fun onDestroy() {
    super.onDestroy()
    Cryptchat.currentChatView = null
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    Cryptchat.currentChatView = this
    setContentView(R.layout.activity_main)
    messagesContainer.apply {
      setHasFixedSize(true)
      layoutManager = viewManager
      adapter = viewAdapter
    }
    sendButton.setOnClickListener {
      if (composer.text.isEmpty()) return@setOnClickListener
      val params = JSONObject()
      val message = JSONObject()
      message.put("body", composer.text)
      params.put("message", message)
      addToMessagesAndNotify(composer.text.toString())
      composer.text.clear()
    }
  }

  override fun onClick(position: Int) {
  }

  override fun onBindViewHolder(holder: Adapter.ListItemHolder, position: Int) {
    holder.view.displayName.text = dataset[position]
  }

  fun addToMessagesAndNotify(newItem: String) {
    dataset.add(newItem)
    viewAdapter.notifyItemInserted(dataset.size)
  }
}
