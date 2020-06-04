package cc.osama.cryptchat.ui

import android.os.Bundle
import android.util.Log.w
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import cc.osama.cryptchat.*
import cc.osama.cryptchat.db.Message
import cc.osama.cryptchat.db.Server
import cc.osama.cryptchat.db.User
import kotlinx.android.synthetic.main.activity_chat_view.*
import kotlinx.android.synthetic.main.chat_message.view.*
import kotlin.collections.ArrayList

class ChatView : RecyclerViewImplementer<ChatView.DisplayMessageStruct>() {
  data class DisplayMessageStruct(
    val plaintext: String,
    val id: Long,
    val status: Int
  ) {
    constructor(message: Message) : this(
      id = message.id,
      plaintext = message.plaintext,
      status = message.status
    )
  }
  override val dataset = ArrayList<DisplayMessageStruct>()
  override val layout = R.layout.chat_message
  override val viewAdapter = Adapter(dataset, layout, this).also {
    // it.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
    //   overr
    // })
  }
  override val viewManager = LinearLayoutManager(this).also { it.stackFromEnd = true }
  private var liveData: LiveData<List<Message>>? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_chat_view)
    chatBody.apply {
      setHasFixedSize(true)
      layoutManager = viewManager
      adapter = viewAdapter
    }
    val user = intent?.extras?.get("user") as User
    val server = intent?.extras?.get("server") as Server
    liveData = getObserver(serverId = server.id, userId = user.id, lastId = 0)
    chatMessageSend.setOnClickListener {
      if (chatMessageInput.text.isNotEmpty()) {
        val plaintext = chatMessageInput.text.toString()
        val handler = OutboundMessageHandler(
          plaintext = plaintext,
          server = server,
          user = user,
          context = applicationContext
        )
        handler.saveToDb {
          // dataset.add(plaintext)
          // viewAdapter.notifyItemInserted(dataset.size)
        }
        handler.encryptAndSend()
      }
    }
  }

  override fun onBindViewHolder(holder: Adapter.ListItemHolder, position: Int) {
    holder.view.myRandomTextHolder.text = dataset[position].plaintext
  }

  private fun getObserver(serverId: Long, userId: Long, lastId: Long = 0) : LiveData<List<Message>> {
    val db = Cryptchat.db(applicationContext)
    val live = db.messages().findByServerAndUserLive(serverId = serverId, userId = userId, lastId = lastId)
    val observer = Observer<List<Message>> { messages ->
      w("TOKEN", "DATASET SIZE ${messages.size}")
      if (messages.isEmpty()) {
        return@Observer
      }
      val positionStart = dataset.size + 1
      dataset.addAll(messages.map { DisplayMessageStruct(it) })
      viewAdapter.notifyItemRangeInserted(positionStart, messages.size)
      live.removeObservers(this)
      chatBody.scrollToPosition(dataset.size - 1)
      synchronized(this) {
        liveData = getObserver(serverId, userId, dataset.maxBy { it.id }?.id ?: 0)
      }
    }
    live.observe(this, observer)
    return live
  }
}
