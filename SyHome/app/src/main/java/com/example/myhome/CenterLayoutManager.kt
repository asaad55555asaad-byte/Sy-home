package com.example.myhome

import android.content.Context
import android.graphics.Rect
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/** Horizontal row that keeps the focused card centred and glides to it (Apple TV style). */
class CenterLayoutManager(ctx: Context) : LinearLayoutManager(ctx, RecyclerView.HORIZONTAL, false) {
    override fun requestChildRectangleOnScreen(
        parent: RecyclerView, child: View, rect: Rect, immediate: Boolean
    ): Boolean {
        val parentCenter = parent.paddingLeft + (parent.width - parent.paddingLeft - parent.paddingRight) / 2
        val dx = (child.left + child.width / 2) - parentCenter
        if (immediate) parent.scrollBy(dx, 0)
        else parent.smoothScrollBy(dx, 0, DecelerateInterpolator(1.4f), 220)
        return true
    }
}
