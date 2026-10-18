package org.dumbtv.launcher.input

/** Validates event sequences; native TV controls still handle clicks, holds and pressed feedback. */
class RemoteOkInput {
    private data class Press(val owner: Any, val downTime: Long, val key: Int, val device: Int,
        var repeated: Boolean = false, var cancelled: Boolean = false)
    private var press: Press? = null
    private var completed: Triple<Long, Int, Int>? = null

    fun down(owner: Any, downTime: Long, key: Int, device: Int, repeat: Int): Boolean {
        val active = press
        if (repeat > 0) {
            // TV Material 1.0 uses the first repeat to recognize a long press.
            if (repeat != 1 || active == null || active.owner !== owner || active.cancelled || active.repeated ||
                active.downTime != downTime || active.key != key || active.device != device) return false
            active.repeated = true
            return true
        }
        if (completed == Triple(downTime, key, device)) return false
        if (active != null && active.downTime == downTime && active.key == key && active.device == device) return false
        press = Press(owner, downTime, key, device)
        return true
    }

    fun up(owner: Any, downTime: Long, key: Int, device: Int, cancelled: Boolean): Boolean {
        val active = press ?: return false
        if (active.downTime != downTime || active.key != key || active.device != device) return false
        press = null
        completed = Triple(downTime, key, device)
        return active.owner === owner && !active.cancelled && !cancelled
    }

    fun cancel(owner: Any) { press?.takeIf { it.owner === owner }?.cancelled = true }
}
