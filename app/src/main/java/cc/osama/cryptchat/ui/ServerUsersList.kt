package cc.osama.cryptchat.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.text.format.DateUtils
import android.util.Log.w
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.LinearLayoutManager
import cc.osama.cryptchat.*
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
  private lateinit var server: Server
  private var serverId: Long = -1

  private val receiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
      refreshConversations()
    }
  }

  companion object {
    fun createIntent(serverId: Long, context: Context) : Intent {
      return Intent(context, ServerUsersList::class.java).also {
        it.putExtra("serverId", serverId)
      }
    }
    const val REFRESH_COMMAND = "REFRESH_SERVER_USERS_LIST"
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    serverId = intent.extras?.getLong("serverId") as Long
    AsyncExec.run {
      server = Cryptchat.db(applicationContext).servers().findById(serverId) as Server
      it.execMainThread {
        setContentView(R.layout.activity_server_users_list)
        usersList.apply {
          setHasFixedSize(true)
          layoutManager = viewManager
          adapter = viewAdapter
        }
        setSupportActionBar(serverUsersListToolbar)
        supportActionBar?.title = server.name ?: "Server"
      }
    }
  }

  override fun onCreateOptionsMenu(menu: Menu?): Boolean {
    menuInflater.inflate(R.menu.server_users_list, menu)
    return true
  }

  override fun onOptionsItemSelected(item: MenuItem): Boolean {
    if (item.itemId == R.id.go_to_server_settings) {
      startActivity(ServerSettings.createIntent(server.id, this))
    } else {
      return super.onOptionsItemSelected(item)
    }
    return true
  }

  override fun onStart() {
    super.onStart()
    AsyncExec.run {
      // TODO: Maybe it's worth adding a check here in case the server
      // is deleted and redirect the user if it's deleted.
      server = Cryptchat.db(applicationContext).servers().findById(serverId) as Server
      it.execMainThread {
        LocalBroadcastManager.getInstance(this).registerReceiver(receiver, IntentFilter(REFRESH_COMMAND))
        supportActionBar?.title = server.name ?: "Server"
        refreshConversations()
      }
    }
  }

  override fun onStop() {
    super.onStop()
    LocalBroadcastManager.getInstance(this).unregisterReceiver(receiver)
  }

  fun refreshConversations() {
    val db = Cryptchat.db(applicationContext)
    AsyncExec.run {
      val newSet = db.users().findConversationsOnServer(serverId = server.id)
      it.execMainThread {
        dataset.removeAll(dataset)
        dataset.addAll(newSet)
        viewAdapter.notifyDataSetChanged()
      }
    }
  }

  override fun onClick(position: Int) {
    val user = dataset[position].user
    val intent = ChatView.createIntent(server = server, user = user, context = this)
    startActivity(intent)
  }

  override fun onBindViewHolder(holder: Adapter.ListItemHolder, position: Int) {
    val conversation = dataset[position]
    val user = conversation.user
    holder.view.displayName.text = user.name ?: user.countryCode + user.phoneNumber
    holder.view.lastMessageContainer.text = conversation.lastMessage ?: ""
    if (user.avatarUrl != null) {
      val bitmap = AvatarsStore(server.id, user.id, applicationContext).bitmap(AvatarsStore.Companion.Sizes.Small)
      if (bitmap != null) {
        holder.view.avatarHolder.setImageBitmap(bitmap)
      }
    }
    if (conversation.lastMessageDate != null) {
      val formatter = if (DateUtils.isToday(conversation.lastMessageDate)) {
        SimpleDateFormat("HH:mm", Locale.getDefault())
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
