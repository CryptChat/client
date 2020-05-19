package cc.osama.cryptchat.ui

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log.w
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import cc.osama.cryptchat.R
import cc.osama.cryptchat.Cryptchat
import cc.osama.cryptchat.db.Server
import kotlinx.android.synthetic.main.activity_servers_list.*
import kotlinx.android.synthetic.main.servers_list_item.view.*
import javax.net.ssl.SSLContext

interface OnServerClick {
  fun onServerClick(position: Int)
}

class ServersList : AppCompatActivity(), OnServerClick {
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
    w("SERVER CLICK", "SERVER INFO: name: ${server.name}, address: ${server.address}, id: ${server.id}, userId: ${server.userId}")
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_servers_list)
    val engine = SSLContext.getInstance("TLS").createSSLEngine()
    engine.enabledProtocols.forEach {
      w("WARRRRN", it?.toString() ?: "NOTHING")
    }
    return

    serversList.apply {
      setHasFixedSize(true)
      layoutManager = viewManager
      adapter = viewAdapter
    }
    val db = Cryptchat.db(applicationContext)
    db.asyncExec({
      db.server().getAll().forEach {
        servers.add(it)
      }
    },
    after = {
      viewAdapter.notifyDataSetChanged()
    })
  }
}
