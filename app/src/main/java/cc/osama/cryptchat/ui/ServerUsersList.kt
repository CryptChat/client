package cc.osama.cryptchat.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.text.format.DateUtils
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.FrameLayout
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

  private val receiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
      refreshConversations()
    }
  }

  companion object {
    private const val REFRESH_INTENT_ACTION = "CRYPTCHAT_REFRESH_SERVER_USERS_LIST"
    fun createIntent(server: Server, context: Context) : Intent {
      return Intent(context, ServerUsersList::class.java).also {
        it.putExtra("server", server)
      }
    }

    fun refreshUsersList(context: Context) {
      LocalBroadcastManager.getInstance(context).also { broadcast ->
        val intent = Intent(REFRESH_INTENT_ACTION)
        broadcast.sendBroadcast(intent)
      }
    }
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    server = intent.extras?.get("server") as Server
    setContentView(R.layout.activity_server_users_list)
    usersList.apply {
      setHasFixedSize(true)
      layoutManager = viewManager
      adapter = viewAdapter
    }
    setSupportActionBar(serverUsersListToolbar)
    supportActionBar?.setDisplayHomeAsUpEnabled(true)
  }

  override fun onCreateOptionsMenu(menu: Menu?): Boolean {
    menuInflater.inflate(R.menu.server_users_list, menu)
    return true
  }

  override fun onPrepareOptionsMenu(menu: Menu?): Boolean {
    menu?.findItem(R.id.go_to_admin_interface)?.isVisible = server.isAdmin
    return super.onPrepareOptionsMenu(menu)
  }

  override fun onOptionsItemSelected(item: MenuItem): Boolean {
    when (item.itemId) {
      R.id.go_to_server_settings -> {
        startActivity(ServerSettings.createIntent(server, this))
      }
      R.id.go_to_admin_interface -> {
        startActivity(AdminWebView.createIntent(server, this))
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

  override fun onStart() {
    super.onStart()
    LocalBroadcastManager
      .getInstance(this)
      .registerReceiver(receiver, IntentFilter(REFRESH_INTENT_ACTION))
    AsyncExec.run(AsyncExec.Companion.Threads.Db) {
      server.reload(applicationContext)
      AsyncExec.onUiThread {
        supportActionBar?.title = server.name ?: resources.getString(R.string.server)
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
        dataset.clear()
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
      val bitmap = AvatarsStore(server.id, user.id, applicationContext).bitmap(AvatarsStore.Sizes.Small)
      if (bitmap != null) {
        holder.view.avatarHolder.setImageBitmap(bitmap)
        holder.view.avatarHolder.layoutParams = FrameLayout.LayoutParams(
          FrameLayout.LayoutParams.MATCH_PARENT,
          FrameLayout.LayoutParams.MATCH_PARENT
        )
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
