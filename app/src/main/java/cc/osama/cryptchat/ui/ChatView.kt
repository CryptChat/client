package cc.osama.cryptchat.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log.w
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import cc.osama.cryptchat.*
import cc.osama.cryptchat.db.Message
import cc.osama.cryptchat.db.Server
import cc.osama.cryptchat.db.User
import kotlinx.android.synthetic.main.activity_chat_view.*
import kotlinx.android.synthetic.main.chat_message_first_party.view.*
import kotlin.collections.ArrayList

class ChatView : RecyclerViewImplementer<ChatView.DisplayMessageStruct>() {
  class DisplayMessageStruct(
    val plaintext: String,
    val id: Long,
    val status: Int
  ) {
    constructor(message: Message) : this(
      id = message.id,
      plaintext = message.plaintext,
      status = message.status
    )

    fun isFirstParty() = status < Message.UNDECRYPTED
  }
  override val dataset = ArrayList<DisplayMessageStruct>()
  override val defaultLayout = R.layout.chat_message_first_party
  override val viewAdapter = Adapter(dataset, defaultLayout, this)
  override val viewManager = LinearLayoutManager(this)
  private var liveData: LiveData<List<Message>>? = null

  companion object {
    fun createIntent(server: Server, user: User, context: Context) : Intent {
      return Intent(context, ChatView::class.java).also {
        it.putExtra("user", user)
        it.putExtra("server", server)
      }
    }
  }
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
    AsyncExec.run {
      Cryptchat.db(applicationContext).messages().setMessagesReadByServerAndUser(serverId = server.id, userId = user.id)
    }
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
          handler.process()
        }
      }
    }
  }

  override fun onBindViewHolder(holder: Adapter.ListItemHolder, position: Int) {
    holder.view.messageTextHolder.text = dataset[position].plaintext
  }

  override fun getItemViewType(position: Int): Int {
    return if (dataset[position].isFirstParty()) {
      super.getItemViewType(position)
    } else {
      R.layout.chat_message_second_party
    }
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
