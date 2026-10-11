package com.pokedaisy.app

import android.app.Activity
import android.app.ActivityOptions
import android.content.Context
import android.os.Bundle
import android.graphics.Point
import android.hardware.display.DisplayManager
import android.view.Display

/** The device's screens: is there a second one, and which. */
object Screens {
    /**
     * The second screen, or null with one. The Thor's bottom screen is a
     * presentation display. Other dual-screen handhelds (Retroid Pocket Duo /
     * Duo Lite) may expose theirs as a plain secondary display without that
     * flag, so fall back to any valid public display other than the built-in
     * main one.
     */
    fun second(context: Context): Display? {
        val displays = context.getSystemService(DisplayManager::class.java) ?: return null
        return displays.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION).firstOrNull { it.isValid }
            ?: displays.displays.firstOrNull {
                it.isValid && it.displayId != Display.DEFAULT_DISPLAY && (it.flags and Display.FLAG_PRIVATE) == 0
            }
    }

    /**
     * Start options that put the game on the main display, for an [activity] that isn't on it - the
     * library or a frontend opened on the bottom screen. A started activity otherwise opens on its
     * starter's display: the game ran on the bottom screen, and [second] (that same screen) got the
     * companion over it, the top screen nothing. With these (and FLAG_ACTIVITY_NEW_TASK) Android
     * moves the app's task to the main display, so the library comes back there after the game too.
     * SWAP SCREENS still works from there: the activity swaps its windows' contents itself. Null on
     * the main display already.
     */
    fun mainDisplayLaunch(activity: Activity): Bundle? {
        @Suppress("DEPRECATION") // Activity.display is API 30+; the window's display is the activity's on every version.
        val here = activity.windowManager.defaultDisplay.displayId
        if (here == Display.DEFAULT_DISPLAY) return null
        return ActivityOptions.makeBasic().setLaunchDisplayId(Display.DEFAULT_DISPLAY).toBundle()
    }

    /** SWAP SCREENS only means something with two. */
    fun hasSecond(context: Context) = second(context) != null

    /** The second screen's width / height, or null with one. */
    fun secondAspect(context: Context): Float? {
        val size = Point().also { p -> second(context)?.let { @Suppress("DEPRECATION") it.getRealSize(p) } ?: return null }
        return if (size.x > 0 && size.y > 0) size.x.toFloat() / size.y else null
    }
}
