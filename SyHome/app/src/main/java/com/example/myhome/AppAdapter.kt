package com.example.myhome

import android.content.Context
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/** Square icon tiles in a horizontal shelf. Long shelves (8+) loop endlessly. */
class AppAdapter(
    private val onClick: (AppItem) -> Unit,
    private val onLong: (AppItem) -> Unit
) : RecyclerView.Adapter<AppAdapter.VH>() {

    private var items = listOf<AppItem>()
    var accent: Int = Style.DEFAULT_ACCENT
    var tileDp: Int = 96
    var onFocus: ((AppItem, Int, Int) -> Unit)? = null
    private val half = Int.MAX_VALUE / 2
    private var tileAccent = 0
    private var tileState: Drawable.ConstantState? = null

    fun isRing() = items.size >= 8
    fun size() = items.size

    fun submit(list: List<AppItem>) {
        items = list
        notifyDataSetChanged()
    }

    fun refresh() = notifyDataSetChanged()

    fun indexOf(pkg: String) = items.indexOfFirst { it.pkg == pkg }

    /** Adapter position of list index [idx], placed in the middle of the loop. */
    fun startPosition(idx: Int): Int = if (isRing()) half - half % items.size + idx else idx

    fun positionOf(pkg: String): Int {
        val i = indexOf(pkg)
        return if (i < 0) -1 else startPosition(i)
    }

    private fun itemAt(pos: Int): AppItem = items[if (isRing()) pos % items.size else pos]

    private fun tileBackground(ctx: Context): Drawable {
        val st = tileState
        if (st == null || tileAccent != accent) {
            val d = Style.tile(ctx, accent)
            tileState = d.constantState
            tileAccent = accent
            return d
        }
        return st.newDrawable().mutate()
    }

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val icon: ImageView = v.findViewById(R.id.icon)
        val label: TextView = v.findViewById(R.id.label)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_app, parent, false))

    override fun getItemCount() = if (isRing()) Int.MAX_VALUE else items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = itemAt(position)
        val v = holder.itemView
        val d = v.resources.displayMetrics.density
        val tile = (tileDp * d).toInt()
        val lp = v.layoutParams
        if (lp != null) {
            lp.width = tile
            lp.height = tile
            v.layoutParams = lp
        }
        val ilp = holder.icon.layoutParams
        ilp.height = tile - (20 * d).toInt()
        holder.icon.layoutParams = ilp
        holder.label.visibility = View.GONE

        v.background = tileBackground(v.context)
        holder.icon.setImageDrawable(item.icon)
        v.setOnClickListener { onClick(item) }
        v.setOnLongClickListener { onLong(item); true }
        v.alpha = 0.92f
        v.scaleX = 1f
        v.scaleY = 1f
        v.setOnFocusChangeListener { view, focused ->
            val s = if (focused) 1.12f else 1f
            view.animate().scaleX(s).scaleY(s)
                .alpha(if (focused) 1f else 0.92f)
                .setInterpolator(DecelerateInterpolator(1.5f))
                .setDuration(180).start()
            if (focused) {
                val pos = holder.bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION && items.isNotEmpty()) {
                    val idx = if (isRing()) pos % items.size else pos
                    onFocus?.invoke(item, idx, items.size)
                }
            }
        }
    }
}
