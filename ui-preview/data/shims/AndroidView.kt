package android.view

class KeyEvent {
    companion object {
        fun keyCodeFromString(s: String): Int = s.hashCode() and 0xFFFF
        fun keyCodeToString(c: Int): String = "KEYCODE_$c"
        const val KEYCODE_BACK = 4
        const val KEYCODE_BUTTON_L2 = 104
        const val KEYCODE_BUTTON_R2 = 105
    }
}

/** Joystick motion (Settings' analog L2 / R2 capture): never sent in the preview. */
class MotionEvent {
    fun isFromSource(source: Int): Boolean = false
    fun getAxisValue(axis: Int): Float = 0f
    companion object {
        const val AXIS_LTRIGGER = 17
        const val AXIS_RTRIGGER = 18
        const val AXIS_THROTTLE = 19
        const val AXIS_GAS = 22
        const val AXIS_BRAKE = 23
    }
}

class InputDevice {
    companion object {
        const val SOURCE_JOYSTICK = 0x01000010
    }
}
