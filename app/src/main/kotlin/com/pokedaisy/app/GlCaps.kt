package com.pokedaisy.app

import android.app.ActivityManager
import android.content.Context

/** What the device's GPU can do, for settings that depend on it (ui-preview has a stand-in). */
object GlCaps {
    /** OpenGL ES 3.0 or newer: [EmulatorView] then asks for an ES 3 context, which SHADERS > SMOOTHING needs. */
    fun gles3(context: Context): Boolean =
        (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager)
            .deviceConfigurationInfo.reqGlEsVersion >= 0x30000
}
