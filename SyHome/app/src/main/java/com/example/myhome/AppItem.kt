package com.example.myhome

import android.content.ComponentName
import android.graphics.drawable.Drawable

data class AppItem(
    val label: String,
    val pkg: String,
    val component: ComponentName,
    val icon: Drawable
)
