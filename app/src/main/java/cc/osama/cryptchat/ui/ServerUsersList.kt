package cc.osama.cryptchat.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log.w
import androidx.recyclerview.widget.LinearLayoutManager
import cc.osama.cryptchat.Cryptchat
import cc.osama.cryptchat.R
import cc.osama.cryptchat.RecyclerViewImplementer
import cc.osama.cryptchat.db.Server
import cc.osama.cryptchat.db.User
import kotlinx.android.synthetic.main.activity_server_users_list.*
import kotlinx.android.synthetic.main.server_users_list_item.view.*

class ServerUsersList : RecyclerViewImplementer<User>() {
  override val dataset = ArrayList<User>()
  override val layout = R.layout.server_users_list_item
  override val viewAdapter = Adapter(dataset, layout, this)
  override val viewManager = LinearLayoutManager(this)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_server_users_list)
    usersList.apply {
      setHasFixedSize(true)
      layoutManager = viewManager
      adapter = viewAdapter
    }
    val server = intent.extras?.get("server") as? Server
    if (server != null) {
      val db = Cryptchat.db(applicationContext)
      db.asyncExec({
        db.users().findByServerId(server.id).forEach { user ->
          dataset.add(user)
        }
      })
    }
  }

  override fun onClick(position: Int) {
    val user = dataset[position]
    val server = intent.extras?.get("server") as? Server
    val intent = Intent(this, ChatView::class.java)
    intent.putExtra("user", user)
    intent.putExtra("server", server)
    startActivity(intent)
  }

  override fun onBindViewHolder(holder: Adapter.ListItemHolder, position: Int) {
    val user = dataset[position]
    holder.view.displayName.text = user.name ?: user.countryCode + user.phoneNumber
  }
}
