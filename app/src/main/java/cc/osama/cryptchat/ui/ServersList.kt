package cc.osama.cryptchat.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log.d
import android.view.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import cc.osama.cryptchat.AsyncExec
import cc.osama.cryptchat.Cryptchat
import cc.osama.cryptchat.R
import cc.osama.cryptchat.db.Server
import kotlinx.android.synthetic.main.activity_servers_list.*
import kotlinx.android.synthetic.main.servers_list_item.view.*


interface OnServerClick {
  fun onServerClick(position: Int)
}

class ServersList : AppCompatActivity(), OnServerClick {
  companion object {
    fun createIntent(context: Context) : Intent {
      return Intent(context, ServersList::class.java)
    }
  }

  private val servers = ArrayList<Server>()
  private val viewAdapter = ServersAdapter(servers, this)
  private val viewManager = LinearLayoutManager(this)

  class ServersAdapter(private val dataset: ArrayList<Server>, private val listener: OnServerClick) : RecyclerView.Adapter<ServersAdapter.ServersListItemHolder>() {
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

    override fun getItemCount(): Int = dataset.size
    override fun onBindViewHolder(holder: ServersListItemHolder, position: Int) {
      holder.view.serverAddress.text = dataset[position].address
      holder.view.serverName.text = dataset[position].name
    }
  }

  override fun onServerClick(position: Int) {
    val server = servers[position]
    startActivity(ServerUsersList.createIntent(server = server, context = this))
  }

  override fun onCreate(savedInstanceState: Bundle?) {
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
    val db = Cryptchat.db(applicationContext)
    AsyncExec.run { runner ->
      servers.clear()
      servers.addAll(db.servers().getAll())
      runner.execMainThread {
        viewAdapter.notifyDataSetChanged()
      }
    }
  }

  override fun onPrepareOptionsMenu(menu: Menu?): Boolean {
    if (menu != null && menu.size() == 0) {
      // inflate only when menu has never been inflated
      // inflating every time would result in duplicate
      // items every time the menu is opened
      menuInflater.inflate(R.menu.servers_list, menu)
    }
    val addServerItem = menu?.findItem(R.id.go_to_add_server_screen)
    if (addServerItem != null) {
      addServerItem.isVisible = !Cryptchat.isReadonly(applicationContext)
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
      else -> {
        return super.onOptionsItemSelected(item)
      }
    }
    return true
  }
}
