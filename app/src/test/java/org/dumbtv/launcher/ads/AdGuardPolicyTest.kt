package org.dumbtv.launcher.ads

import org.junit.Assert.*
import org.junit.Test

class AdGuardPolicyTest {
    @Test fun coversImmediatelyButRequiresTwoAbsentSamplesToRestore() {
        val policy = AdGuardPolicy()
        assertFalse(policy.observe(false))
        assertTrue(policy.observe(true))
        assertTrue(policy.observe(false))
        assertFalse(policy.observe(false))
    }
    @Test fun unknownAndNewAdResetEndConfirmation() {
        val policy = AdGuardPolicy()
        policy.observe(true)
        policy.observe(false)
        assertTrue(policy.observe(null))
        assertTrue(policy.observe(false))
        assertTrue(policy.observe(true))
        assertTrue(policy.observe(false))
        assertFalse(policy.observe(false))
    }
}
