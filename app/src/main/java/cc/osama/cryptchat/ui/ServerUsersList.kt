package cc.osama.cryptchat.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.text.format.DateUtils
import android.util.Log.w
import android.view.View
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.LinearLayoutManager
import cc.osama.cryptchat.AsyncExec
import cc.osama.cryptchat.Cryptchat
import cc.osama.cryptchat.R
import cc.osama.cryptchat.RecyclerViewImplementer
import cc.osama.cryptchat.db.Server
import cc.osama.cryptchat.db.User
import kotlinx.android.synthetic.main.activity_server_users_list.*
import kotlinx.android.synthetic.main.server_users_list_item.view.*
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList

class ServerUsersList : RecyclerViewImplementer<User.Conversation>() {
  override val dataset = ArrayList<User.Conversation>()
  override val defaultLayout = R.layout.server_users_list_item
  override val viewAdapter = Adapter(dataset, defaultLayout, this)
  override val viewManager = LinearLayoutManager(this)

  private val receiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
      refreshConversations()
    }
  }

  companion object {
    fun createIntent(server: Server, context: Context) : Intent {
      return Intent(context, ServerUsersList::class.java).also {
        it.putExtra("server", server)
      }
    }
    const val REFRESH_COMMAND = "REFRESH_SERVER_USERS_LIST"
  }
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_server_users_list)
    usersList.apply {
      setHasFixedSize(true)
      layoutManager = viewManager
      adapter = viewAdapter
    }
  }

  override fun onStart() {
    super.onStart()
    LocalBroadcastManager.getInstance(this).registerReceiver(receiver, IntentFilter(REFRESH_COMMAND))
    refreshConversations()
  }

  override fun onStop() {
    super.onStop()
    LocalBroadcastManager.getInstance(this).unregisterReceiver(receiver)
  }

  fun refreshConversations() {
    val server = intent.extras?.get("server") as Server
    val db = Cryptchat.db(applicationContext)
    dataset.removeAll(dataset)
    AsyncExec.run {
      dataset.addAll(db.users().findConversationsOnServer(serverId = server.id))
      it.execMainThread {
        viewAdapter.notifyDataSetChanged()
      }
    }
  }

  override fun onClick(position: Int) {
    val user = dataset[position].user
    val server = intent.extras?.get("server") as Server
    val intent = ChatView.createIntent(server = server, user = user, context = this)
    startActivity(intent)
  }

  override fun onBindViewHolder(holder: Adapter.ListItemHolder, position: Int) {
    val conversation = dataset[position]
    val user = conversation.user
    holder.view.displayName.text = user.name ?: user.countryCode + user.phoneNumber
    holder.view.lastMessageContainer.text = conversation.lastMessage ?: ""
    if (conversation.lastMessageDate != null) {
      val formatter = if (DateUtils.isToday(conversation.lastMessageDate)) {
        SimpleDateFormat("HH:mm a", Locale.getDefault())
      } else {
        SimpleDateFormat("dd/MM/yy", Locale.getDefault())
      }
      holder.view.conversationDateHolder.visibility = View.VISIBLE
      holder.view.conversationDateHolder.text = formatter.format(Date(conversation.lastMessageDate))
    } else {
      holder.view.conversationDateHolder.visibility = View.INVISIBLE
    }
    if (conversation.unreadCount > 0) {
      holder.view.conversationUnreadCountHolder.visibility = View.VISIBLE
      holder.view.conversationUnreadCountHolder.text = conversation.unreadCount.toString()
    } else {
      holder.view.conversationUnreadCountHolder.visibility = View.INVISIBLE
    }
  }
}
