package cc.osama.sechat

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.synthetic.main.conversation_row.view.*

class ConversationAdapter(private val dataset: ArrayList<HashMap<String, String>>) : RecyclerView.Adapter<ConversationAdapter.ViewHolder>() {
  class ViewHolder(val view: View): RecyclerView.ViewHolder(view)

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
    val view = LayoutInflater.from(parent.context).inflate(R.layout.conversation_row, parent, false)
    return ViewHolder(view)
  }

  override fun getItemCount() = dataset.size

  override fun onBindViewHolder(holder: ViewHolder, position: Int) {
    holder.view.username.text = dataset[position]["name"]
    holder.view.last.text = dataset[position]["last"]
  }
}
