package org.dumbtv.launcher.ads

/** Null observations are OCR/capture failures, never evidence that an ad ended. */
class AdGuardPolicy {
    var covered = false
        private set
    private var absent = 0
    fun observe(ad: Boolean?): Boolean {
        when (ad) {
            true -> { covered = true; absent = 0 }
            false -> { if (++absent >= 2) covered = false }
            null -> absent = 0
        }
        return covered
    }
}
