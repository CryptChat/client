package cc.osama.cryptchat

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.synthetic.main.activity_conversation.*

interface OnClick {
  fun onClick(position: Int)
  fun onBindViewHolder(holder: RecyclerViewImplementer.Adapter.ListItemHolder, position: Int)
}

abstract class RecyclerViewImplementer<T> : AppCompatActivity(), OnClick {
  abstract val dataset: ArrayList<T>
  abstract val layout: Int
  abstract val viewAdapter: Adapter<T>
  val viewManager = LinearLayoutManager(this)

  class Adapter<T>(
    private val dataset: ArrayList<T>,
    private val layout: Int,
    private val listener: OnClick
  ) : RecyclerView.Adapter<Adapter.ListItemHolder>() {
    class ListItemHolder(val view: View, private val listener: OnClick) : RecyclerView.ViewHolder(view) {
      init {
        view.setOnClickListener {
          listener.onClick(adapterPosition)
        }
      }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ListItemHolder {
      Log.w("LOOOOOOOOOG", "onCreateViewHolder")
      val view = LayoutInflater.from(parent.context).inflate(layout, parent, false)
      return ListItemHolder(view, listener)
    }

    override fun getItemCount() = dataset.size

    override fun onBindViewHolder(holder: ListItemHolder, position: Int) {
      Log.w("LOOOOOOOOOG", "onBindViewHolder")
      listener.onBindViewHolder(holder, position)
    }
  }
}