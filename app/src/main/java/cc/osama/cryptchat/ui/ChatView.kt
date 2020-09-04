package cc.osama.cryptchat.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
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
    val createdAt: Long,
    val byMe: Boolean
  ) {
    constructor(message: Message) : this(
      id = message.id,
      plaintext = message.plaintext,
      status = message.status,
      createdAt = message.createdAt,
      byMe = message.byMe()
    )
  }
  override val dataset = ArrayList<DisplayMessageStruct>()
  override val defaultLayout = R.layout.chat_message_first_party
  override val viewAdapter = Adapter(dataset, defaultLayout, this)
  override val viewManager = LinearLayoutManager(this)
  private lateinit var server: Server
  private lateinit var user: User

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
    user = intent?.extras?.get("user") as User
    server = intent?.extras?.get("server") as Server
    chatBody.apply {
      setHasFixedSize(true)
      layoutManager = viewManager
      adapter = viewAdapter
    }
    setSupportActionBar(chatViewToolbar)
    chatMessageSend.addTextChangedListener(CryptchatTextWatcher(
      on = { s, _, _, _ ->
        chatMessageSend.isEnabled = s != null && s.trim().isNotEmpty()
      }
    ))
    chatMessageSend.setOnClickListener {
      val plaintext = chatMessageInput.text.toString().trim()
      if (plaintext.isNotEmpty()) {
        AsyncExec.run {
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
    refreshMessagesStream()
    LocalBroadcastManager.getInstance(this).registerReceiver(receiver, IntentFilter(BROADCAST_INTENT))
  }

  private fun refreshMessagesStream() {
    AsyncExec.run {
      synchronized(dataset) {
        val lastId = dataset.maxBy { m -> m.id }?.id ?: 0
        val messages = db().messages().findConversationMessages(
          serverId = server.id,
          userId = user.id,
          lastId = lastId
        )
        if (messages.isEmpty()) return@run
        dataset.addAll(messages.map { m -> DisplayMessageStruct(m) })
        db().messages().setMessagesReadByServerAndUser(serverId = server.id, userId = user.id)
      }
      it.execMainThread {
        viewAdapter.notifyDataSetChanged()
        chatBody.scrollToPosition(dataset.size - 1)
      }
    }
  }

  private fun refreshMessage(messageId: Long) {
    AsyncExec.run {
      val message = db().messages().findById(messageId) ?: return@run
      it.execMainThread {
        synchronized(dataset) {
          val index = dataset.indexOfFirst { m -> m.id == messageId }
          if (index != -1) {
            dataset[index] = DisplayMessageStruct(message)
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
    return if (dataset[position].byMe) {
      super.getItemViewType(position)
    } else {
      R.layout.chat_message_second_party
    }
  }

  override fun onCreateOptionsMenu(menu: Menu?): Boolean {
    menuInflater.inflate(R.menu.chat_view, menu)
    return true
  }

  override fun onOptionsItemSelected(item: MenuItem): Boolean {
    if (item.itemId == R.id.verify_contact_identity_key) {
      startActivity(VerifyIdentity.createIntent(user, server, applicationContext))
    } else {
      return super.onOptionsItemSelected(item)
    }
    return true
  }

  private fun db() = Cryptchat.db(applicationContext)
}
