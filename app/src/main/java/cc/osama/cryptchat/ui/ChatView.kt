package cc.osama.cryptchat.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.FrameLayout
import androidx.appcompat.app.AlertDialog
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSmoothScroller
import androidx.recyclerview.widget.RecyclerView
import cc.osama.cryptchat.*
import cc.osama.cryptchat.db.Message
import cc.osama.cryptchat.db.Server
import cc.osama.cryptchat.db.User
import kotlinx.android.synthetic.main.activity_chat_view.*
import kotlinx.android.synthetic.main.chat_system_message.view.*
import kotlinx.android.synthetic.main.sent_chat_message.view.*
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList

class ChatView : RecyclerViewImplementer<ChatView.ChatRowItem>() {
  interface ChatRowItem
  class ChatMessage(
    val plaintext: String,
    val id: Long,
    val status: Int,
    val createdAt: Long,
    val byMe: Boolean
  ) : ChatRowItem {
    constructor(message: Message) : this(
      id = message.id,
      plaintext = message.plaintext,
      status = message.status,
      createdAt = message.createdAt,
      byMe = message.byMe()
    )
  }
  class ChatSystemMessage(val content: String) : ChatRowItem

  override val dataset = ArrayList<ChatRowItem>()
  override val defaultLayout = R.layout.sent_chat_message
  override val viewAdapter = Adapter(dataset, defaultLayout, this)
  override val viewManager = LinearLayoutManager(this).apply {
    stackFromEnd = false
  }
  private lateinit var server: Server
  private lateinit var user: User

  private var lastVisibleItemPosition = -1
  private var didInitialScroll = false

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
    userNameTextView.text = user.displayName()
    supportActionBar?.title = user.displayName()
    supportActionBar?.setDisplayHomeAsUpEnabled(true)
    supportActionBar?.setDisplayShowTitleEnabled(true)
    val avatarBitmap = AvatarsStore(server.id, user.id, applicationContext).bitmap(AvatarsStore.Sizes.Small)
    if (avatarBitmap != null) {
      avatarImageView.setImageBitmap(avatarBitmap)
      avatarImageView.layoutParams = FrameLayout.LayoutParams(
        FrameLayout.LayoutParams.MATCH_PARENT,
        FrameLayout.LayoutParams.MATCH_PARENT
      )
    }
    chatMessageSend.isEnabled = chatMessageInput.text.toString().trim().isNotEmpty()
    chatMessageInput.addTextChangedListener(CryptchatTextWatcher(
      on = { s, _, _, _ ->
        chatMessageSend.isEnabled = s != null && s.trim().isNotEmpty()
      }
    ))
    chatMessageSend.setOnClickListener {
      val plaintext = chatMessageInput.text.toString().trim()
      if (plaintext.isNotEmpty()) {
        AsyncExec.run(AsyncExec.Companion.Threads.Db) {
          val handler = OutboundMessageHandler(
            plaintext = plaintext,
            server = server,
            user = user,
            context = applicationContext
          )
          handler.saveToDb {
            AsyncExec.run(AsyncExec.Companion.Threads.Network) {
              handler.process()
              val error = handler.getError()
              if (error != null && error.isClientError) {
                AsyncExec.onUiThread {
                  val message = if (error.serverMessages.isNotEmpty()) {
                    error.serverMessages.joinToString("\n")
                  } else {
                    resources.getString(R.string.chat_view_server_rejected_message_no_reason)
                  }
                  AlertDialog.Builder(this).apply {
                    setNegativeButton(R.string.dialog_ok) { _, _ ->  }
                    setTitle(R.string.chat_view_server_rejected_message_title)
                    setMessage(message)
                    create().show()
                  }
                }
              }
            }
          }
        }
        chatMessageInput.text.clear()
      }
    }
    lastVisibleItemPosition = viewManager.findLastVisibleItemPosition()
    chatBody.addOnScrollListener(object : RecyclerView.OnScrollListener() {
      override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
        super.onScrolled(recyclerView, dx, dy)
        if (dy != 0) {
          lastVisibleItemPosition = viewManager.findLastVisibleItemPosition()
        }
      }
    })
    chatBody.addOnLayoutChangeListener { _, _, _, _, bottom, _, _, _, oldBottom ->
      if ((lastVisibleItemPosition + 1 == dataset.size || lastVisibleItemPosition == -1)
        && bottom < oldBottom) {
        LinearSmoothScroller(this).also {
          it.targetPosition = (dataset.size - 1).coerceAtLeast(0)
          viewManager.startSmoothScroll(it)
        }
      }
    }
    dataset.add(ChatSystemMessage(resources.getString(R.string.chat_view_chat_beginning)))
  }

  override fun onStop() {
    super.onStop()
    LocalBroadcastManager.getInstance(this).unregisterReceiver(receiver)
  }

  override fun onStart() {
    super.onStart()
    if (Cryptchat.isReadonly(applicationContext)) {
      chatMessageSend.visibility = View.GONE
      chatMessageInput.isEnabled = false
      chatMessageInput.hint = resources.getText(R.string.chat_view_input_readonly_hint)
    } else {
      chatMessageSend.visibility = View.VISIBLE
      chatMessageInput.isEnabled = true
      chatMessageInput.hint = resources.getText(R.string.chat_view_input_hint)
    }
    refreshMessagesStream()
    LocalBroadcastManager.getInstance(this).registerReceiver(receiver, IntentFilter(BROADCAST_INTENT))
  }

  private fun refreshMessagesStream() {
    AsyncExec.run(AsyncExec.Companion.Threads.Db) {
      val scrollToBottom = viewManager.findLastVisibleItemPosition() + 1 == dataset.size
      synchronized(dataset) {
        val lastId = dataset.filterIsInstance<ChatMessage>().maxBy { m -> m.id }?.id ?: 0
        val messages = db().messages().findConversationMessages(
          serverId = server.id,
          userId = user.id,
          lastId = lastId
        )
        if (messages.isEmpty()) return@run
        messages.forEachIndexed { index, message ->
          val msgDate = Calendar.getInstance()
          msgDate.time = Date(message.createdAt)
          val today = Calendar.getInstance()
          today.time = Date(System.currentTimeMillis())
          val yesterday = Calendar.getInstance()
          yesterday.time = Date(System.currentTimeMillis() - (3600 * 24 * 1000))
          val formatter = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
          var prevMsgDate: Calendar?
          prevMsgDate = Calendar.getInstance()
          if (index > 0) {
            prevMsgDate?.time = Date(messages[index - 1].createdAt)
          } else if (dataset.size > 0) {
            val prev = dataset[dataset.size - 1]
            if (prev is ChatMessage) prevMsgDate?.time = Date(prev.createdAt) else prevMsgDate = null
          } else {
            prevMsgDate = null
          }
          if (prevMsgDate == null) {
            if (datesOnSameDay(msgDate, yesterday)) {
              dataset.add(ChatSystemMessage(resources.getString(R.string.chat_view_yesterday)))
            } else if (!datesOnSameDay(msgDate, today)) {
              dataset.add(ChatSystemMessage(formatter.format(msgDate.time)))
            }
          } else {
            if (!datesOnSameDay(msgDate, prevMsgDate)) {
              when {
                datesOnSameDay(msgDate, today) -> {
                  dataset.add(ChatSystemMessage(resources.getString(R.string.chat_view_today)))
                }
                datesOnSameDay(msgDate, yesterday) -> {
                  dataset.add(ChatSystemMessage(resources.getString(R.string.chat_view_yesterday)))
                }
                else -> {
                  dataset.add(ChatSystemMessage(formatter.format(msgDate.time)))
                }
              }
            }
          }
          dataset.add(ChatMessage(message))
        }
        if (!Cryptchat.isReadonly(applicationContext)) {
          db().messages().setMessagesReadByServerAndUser(serverId = server.id, userId = user.id)
        }
      }
      AsyncExec.onUiThread {
        viewAdapter.notifyDataSetChanged()
        if (scrollToBottom || !didInitialScroll) {
          chatBody.scrollToPosition(dataset.size - 1)
          didInitialScroll = true
        }
      }
    }
  }

  private fun refreshMessage(messageId: Long) {
    AsyncExec.run(AsyncExec.Companion.Threads.Db) {
      val message = db().messages().findById(messageId) ?: return@run
      AsyncExec.onUiThread {
        synchronized(dataset) {
          val index = dataset.indexOfFirst { m -> m is ChatMessage && m.id == messageId }
          if (index != -1) {
            dataset[index] = ChatMessage(message)
            viewAdapter.notifyItemChanged(index)
          }
        }
      }
    }
  }

  override fun onBindViewHolder(holder: Adapter.ListItemHolder, position: Int) {
    val item = dataset[position]
    if (item is ChatMessage) {
      holder.view.messageTextHolder.text = item.plaintext
      SimpleDateFormat("HH:mm", Locale.getDefault()).also { formatter ->
        holder.view.messageDateHolder.text = formatter.format(Date(item.createdAt))
      }
      if (item.byMe) {
        val icon = when (item.status) {
          Message.INITIAL_STATE -> {
            R.drawable.ic_hourglass_empty_black_24dp
          }
          Message.NEEDS_RETRY -> {
            R.drawable.ic_hourglass_empty_black_24dp
          }
          Message.SENT -> {
            R.drawable.ic_check_black_24dp
          }
          else -> {
            R.drawable.ic_error_outline_black_24dp
          }
        }
        holder.view.messageStatusIcon.setImageResource(icon)
      }
    } else if (item is ChatSystemMessage) {
      holder.view.systemMessageHolder.text = item.content
    }
  }

  override fun getItemViewType(position: Int): Int {
    val item = dataset[position]
    return if (item is ChatMessage) {
      if (item.byMe) {
        super.getItemViewType(position)
      } else {
        R.layout.received_chat_message
      }
    } else {
      R.layout.chat_system_message
    }
  }

  override fun onCreateOptionsMenu(menu: Menu?): Boolean {
    menuInflater.inflate(R.menu.chat_view, menu)
    return true
  }

  override fun onOptionsItemSelected(item: MenuItem): Boolean {
    when (item.itemId) {
      R.id.verify_contact_identity_key -> {
        startActivity(VerifyIdentity.createIntent(user, server, this))
      }
      android.R.id.home -> {
        onBackPressed()
      }
      else -> {
        return super.onOptionsItemSelected(item)
      }
    }
    return true
  }

  private fun db() = Cryptchat.db(applicationContext)

  private fun datesOnSameDay(d1: Calendar, d2: Calendar) : Boolean {
    return d1.get(Calendar.DAY_OF_YEAR) == d2.get(Calendar.DAY_OF_YEAR) &&
      d1.get(Calendar.YEAR) == d2.get(Calendar.YEAR)
  }
}
