package cc.osama.cryptchat.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log.w
import cc.osama.cryptchat.Cryptchat
import cc.osama.cryptchat.ECPublicKey
import cc.osama.cryptchat.R
import cc.osama.cryptchat.RecyclerViewImplementer
import cc.osama.cryptchat.db.User
import kotlinx.android.synthetic.main.activity_server_members.*
import kotlinx.android.synthetic.main.users_list_item.view.*

class ServerMembers : RecyclerViewImplementer<User>() {
  override val dataset = ArrayList<User>()
  override val layout = R.layout.users_list_item
  override val viewAdapter = Adapter(dataset, layout, this)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_server_members)
    val serverId = intent.extras?.get("serverId") as? Long
    if (serverId != null) {
      val db = Cryptchat.db(applicationContext)
      db.asyncExec({
        db.users().findByServerId(serverId).forEach { user ->
          dataset.add(user)
        }
        it.execOnUIThread {
          setContentView(R.layout.activity_server_members)
          usersList.apply {
            setHasFixedSize(true)
            layoutManager = viewManager
            adapter = viewAdapter
          }
        }
      })
    }
  }

  override fun onClick(position: Int) {
    val user = dataset[position]
    val intent = Intent(this, Conversation::class.java)
    intent.putExtra("userId", user.id)
    startActivity(intent)
    w("SERVER CLICK", "SERVER INFO: name: ${user.name}, serverId: ${user.serverId}, id: ${user.id}, idOnServer: ${user.idOnServer}")
  }

  override fun onBindViewHolder(holder: Adapter.ListItemHolder, position: Int) {
    val user = dataset[position]
    holder.view.displayName.text = user.name ?: user.countryCode + user.phoneNumber
  }
}
