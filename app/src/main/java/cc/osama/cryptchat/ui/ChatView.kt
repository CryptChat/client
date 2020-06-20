package cc.osama.cryptchat.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.util.Log.w
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.LinearLayoutManager
import cc.osama.cryptchat.*
import cc.osama.cryptchat.db.Message
import cc.osama.cryptchat.db.Server
import cc.osama.cryptchat.db.User
import kotlinx.android.synthetic.main.activity_chat_view.*
import kotlinx.android.synthetic.main.chat_message_first_party.view.*
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList

class ChatView : RecyclerViewImplementer<ChatView.DisplayMessageStruct>() {
  class DisplayMessageStruct(
    val plaintext: String,
    val id: Long,
    val status: Int,
    val createdAt: Long
  ) {
    constructor(message: Message) : this(
      id = message.id,
      plaintext = message.plaintext,
      status = message.status,
      createdAt = message.createdAt
    )

    fun isFirstParty() = status < Message.UNDECRYPTED
  }
  override val dataset = ArrayList<DisplayMessageStruct>()
  override val defaultLayout = R.layout.chat_message_first_party
  override val viewAdapter = Adapter(dataset, defaultLayout, this)
  override val viewManager = LinearLayoutManager(this)

  private val receiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
      if (intent?.getBooleanExtra(BROADCAST_NEW_MESSAGE, false) == true) {
        refreshMessagesStream()
        return
      }
      val messageId = intent?.getLongExtra(BROADCAST_MODIFIED_MESSAGE, 0) ?: 0
      if (messageId > 0) {
        refreshMessage(messageId)
      }
    }
  }

  companion object {
    private const val BROADCAST_INTENT = "CHAT_VIEW_BROADCAST_INTENT"
    private const val BROADCAST_NEW_MESSAGE = "new_message"
    private const val BROADCAST_MODIFIED_MESSAGE = "modified_message"

    fun createIntent(server: Server, user: User, context: Context) : Intent {
      return Intent(context, ChatView::class.java).also {
        it.putExtra("user", user)
        it.putExtra("server", server)
      }
    }

    fun notifyNewMessage(context: Context) {
      LocalBroadcastManager.getInstance(context).also { broadcast ->
        Intent(BROADCAST_INTENT).also { intent ->
          intent.putExtra(BROADCAST_NEW_MESSAGE, true)
          broadcast.sendBroadcast(intent)
        }
      }
    }

    fun notifyModifiedMessage(messageId: Long, context: Context) {
      LocalBroadcastManager.getInstance(context).also { broadcast ->
        Intent(BROADCAST_INTENT).also { intent ->
          intent.putExtra(BROADCAST_MODIFIED_MESSAGE, messageId)
          broadcast.sendBroadcast(intent)
        }
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
    refreshMessagesStream()
    chatMessageSend.addTextChangedListener(CryptchatTextWatcher(
      on = { s, _, _, _ ->
        chatMessageSend.isEnabled = s != null && s.trim().isNotEmpty()
      }
    ))
    chatMessageSend.setOnClickListener {
      if (chatMessageInput.text.trim().isNotEmpty()) {
        val plaintext = chatMessageInput.text.trim().toString()
        val handler = OutboundMessageHandler(
          plaintext = plaintext,
          server = server,
          user = user,
          context = applicationContext
        )
        handler.saveToDb {
          handler.process()
        }
        chatMessageInput.text.clear()
      }
    }
  }

  override fun onStop() {
    super.onStop()
    LocalBroadcastManager.getInstance(this).unregisterReceiver(receiver)
  }

  override fun onStart() {
    super.onStart()
    LocalBroadcastManager.getInstance(this).registerReceiver(receiver, IntentFilter(BROADCAST_INTENT))
  }

  @Synchronized private fun refreshMessagesStream() {
    val user = intent?.extras?.get("user") as User
    val server = intent?.extras?.get("server") as Server
    val maxId = dataset.maxBy { it.id }?.id ?: 0
    Cryptchat.db(applicationContext).also { db ->
      AsyncExec.run {
        val messages = db.messages().findConversationMessages(
          serverId = server.id,
          userId = user.id,
          lastId = maxId
        )
        dataset.addAll(messages.map { m -> DisplayMessageStruct(m) })
        it.execMainThread {
          viewAdapter.notifyDataSetChanged()
          chatBody.scrollToPosition(dataset.size - 1)
        }
        db.messages().setMessagesReadByServerAndUser(serverId = server.id, userId = user.id)
      }
    }
  }

  @Synchronized fun refreshMessage(messageId: Long) {
    Cryptchat.db(applicationContext).also { db ->
      AsyncExec.run {
        val message = db.messages().findById(messageId)
        val index = dataset.indexOfFirst { m -> m.id == messageId }
        if (message != null && index != -1) {
          dataset[index] = DisplayMessageStruct(message)
          it.execMainThread {
            viewAdapter.notifyItemChanged(index)
          }
        }
      }
    }
  }

  override fun onBindViewHolder(holder: Adapter.ListItemHolder, position: Int) {
    holder.view.messageTextHolder.text = dataset[position].plaintext
    SimpleDateFormat("HH:mm", Locale.getDefault()).also { formatter ->
      holder.view.messageDateHolder.text = formatter.format(Date(dataset[position].createdAt))
    }
  }

  override fun getItemViewType(position: Int): Int {
    return if (dataset[position].isFirstParty()) {
      super.getItemViewType(position)
    } else {
      R.layout.chat_message_second_party
    }
  }
}
