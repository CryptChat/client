package cc.osama.cryptchat.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.format.DateUtils
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.URLSpan
import android.view.*
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import cc.osama.cryptchat.AsyncExec
import cc.osama.cryptchat.Cryptchat
import cc.osama.cryptchat.CryptchatBaseAppCompatActivity
import cc.osama.cryptchat.R
import cc.osama.cryptchat.db.Server
import kotlinx.android.synthetic.main.activity_servers_list.*
import kotlinx.android.synthetic.main.servers_list_item.view.*
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList


interface OnServerClick {
  fun onServerClick(position: Int)
}

class ServersList : CryptchatBaseAppCompatActivity(), OnServerClick {
  companion object {
    private const val REFRESH_INTENT_ACTION = "CRYPTCHAT_REFRESH_SERVERS_LIST"
    fun createIntent(context: Context) : Intent {
      return Intent(context, ServersList::class.java)
    }

    fun refreshList(context: Context) {
      LocalBroadcastManager.getInstance(context).also { broadcast ->
        val intent = Intent(REFRESH_INTENT_ACTION)
        broadcast.sendBroadcast(intent)
      }
    }
  }

  private val servers = ArrayList<Server.ServerListItem>()
  private val viewAdapter = ServersAdapter(servers, this, this)
  private val viewManager = LinearLayoutManager(this)

  private val receiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
      refreshList()
    }
  }

  class ServersAdapter(
    private val dataset: ArrayList<Server.ServerListItem>,
    private val listener: OnServerClick,
    private val context: Context
  ) : RecyclerView.Adapter<ServersAdapter.ServersListItemHolder>() {
    class ServersListItemHolder(val view: View, private val listener: OnServerClick) : RecyclerView.ViewHolder(view) {
      init {
        view.setOnClickListener {
          listener.onServerClick(adapterPosition)
        }
      }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ServersListItemHolder {
      val view = LayoutInflater.from(parent.context).inflate(R.layout.servers_list_item, parent, false)
      return ServersListItemHolder(view, listener)
    }

    override fun getItemCount() = dataset.size
    override fun onBindViewHolder(holder: ServersListItemHolder, position: Int) {
      val serverItem = dataset[position]
      holder.view.serverAddress.text = serverItem.server.address
      holder.view.serverName.text = serverItem.server.name
      if (serverItem.lastActivity != null) {
        val formatter = if (DateUtils.isToday(serverItem.lastActivity)) {
          SimpleDateFormat("HH:mm", Locale.getDefault())
        } else {
          SimpleDateFormat("dd/MM/yy", Locale.getDefault())
        }
        holder.view.lastActivityTextView.visibility = View.VISIBLE
        holder.view.lastActivityTextView.text = formatter.format(Date(serverItem.lastActivity))
      } else {
        holder.view.lastActivityTextView.visibility = View.INVISIBLE
      }
      if (serverItem.unreadMessagesCount > 0) {
        holder.view.unreadMessagesCountTextView.visibility = View.VISIBLE
        holder.view.unreadMessagesCountTextView.text = if (serverItem.unreadMessagesCount > 99)
          "+99" else serverItem.unreadMessagesCount.toString()
      } else {
        holder.view.unreadMessagesCountTextView.visibility = View.INVISIBLE
      }
      val count = serverItem.usersCount
      holder.view.usersCountTextView.text = context.resources.getQuantityString(
        R.plurals.servers_list_users_count,
        count,
        count
      )
    }
  }

  override fun onServerClick(position: Int) {
    val server = servers[position].server
    startActivity(ServerUsersList.createIntent(server = server, context = this))
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    setTheme(Cryptchat.defaultTheme(applicationContext))
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_servers_list)

    serversList.apply {
      setHasFixedSize(true)
      layoutManager = viewManager
      adapter = viewAdapter
    }
    setSupportActionBar(serversListToolbar)
  }

  override fun onStart() {
    super.onStart()
    LocalBroadcastManager
      .getInstance(this)
      .registerReceiver(receiver, IntentFilter(REFRESH_INTENT_ACTION))
    refreshList()
  }

  override fun onStop() {
    super.onStop()
    LocalBroadcastManager.getInstance(this).unregisterReceiver(receiver)
  }

  override fun onCreateOptionsMenu(menu: Menu?): Boolean {
    menuInflater.inflate(R.menu.servers_list, menu)
    return super.onCreateOptionsMenu(menu)
  }

  override fun onPrepareOptionsMenu(menu: Menu?): Boolean {
    val addServerItem = menu?.findItem(R.id.go_to_add_server_screen)
    if (addServerItem != null) {
      addServerItem.isVisible = !Cryptchat.isReadonly(applicationContext)
    }
    val darkThemeSwitch = menu?.findItem(R.id.switch_to_dark_theme)
    val lightThemeSwitch = menu?.findItem(R.id.switch_to_light_theme)
    darkThemeSwitch?.isVisible = Cryptchat.defaultTheme(applicationContext) == R.style.AppTheme_Light
    lightThemeSwitch?.isVisible = Cryptchat.defaultTheme(applicationContext) == R.style.AppTheme_Dark
    val restoreBackupItem = menu?.findItem(R.id.go_to_restore_backup)
    if (restoreBackupItem != null) {
      AsyncExec.run {
        val serversCount = Cryptchat.db(applicationContext).servers().getAll().size
        if (serversCount > 0) {
          it.execMainThread {
            restoreBackupItem.isVisible = false
          }
        }
      }
    }
    return super.onPrepareOptionsMenu(menu)
  }

  override fun onOptionsItemSelected(item: MenuItem): Boolean {
    when (item.itemId) {
      R.id.go_to_backup -> {
        startActivity(BackupsEntry.createIntent(this))
      }
      R.id.go_to_add_server_screen -> {
        startActivity(EnterServerAddress.createIntent(this))
      }
      R.id.go_to_restore_backup -> {
        startActivity(RestoreBackup.createIntent(this))
      }
      R.id.switch_to_dark_theme -> {
        Cryptchat.setDarkThemeDefault(applicationContext)
        setTheme(R.style.AppTheme_Dark)
        recreate()
      }
      R.id.switch_to_light_theme -> {
        Cryptchat.resetDefaultTheme(applicationContext)
        setTheme(R.style.AppTheme_Light)
        recreate()
      }
      else -> {
        return super.onOptionsItemSelected(item)
      }
    }
    return true
  }

  private fun refreshList() {
    AsyncExec.run(AsyncExec.Companion.Threads.Db) {
      val db = Cryptchat.db(applicationContext)
      val newList = db.servers().serversList()
      AsyncExec.onUiThread {
        servers.clear()
        servers.addAll(newList)
        viewAdapter.notifyDataSetChanged()
        if (servers.isEmpty()) {
          serversList.visibility = View.GONE
          serversListNoServers.visibility = View.VISIBLE
          val sequence = SpannableStringBuilder(resources.getText(R.string.servers_list_no_servers))
          val underlines = sequence.getSpans(0, sequence.length, URLSpan::class.java)
          for (span in underlines) {
            val start = sequence.getSpanStart(span)
            val end = sequence.getSpanEnd(span)
            val flags = sequence.getSpanFlags(span)
            val clickableSpan = object : ClickableSpan() {
              override fun onClick(widget: View) {
                startActivity(EnterServerAddress.createIntent(this@ServersList))
              }
            }
            sequence.setSpan(clickableSpan, start, end, flags)
            sequence.removeSpan(span)
          }
          serversListNoServers.text = sequence
          serversListNoServers.linksClickable = true
          serversListNoServers.movementMethod = LinkMovementMethod.getInstance()
        } else {
          serversList.visibility = View.VISIBLE
          serversListNoServers.visibility = View.GONE
        }
      }
    }
  }
}
