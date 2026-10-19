package com.example.timeline.media

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProxySettingsTest {
    @Test
    fun onlyDetectedHdrInputsAreToneMappedToSdr() {
        assertFalse(ProxySettings(sourceIsHdr = false, forceSdr = true).shouldToneMapHdrToSdr)
        assertTrue(ProxySettings(sourceIsHdr = true, forceSdr = true).shouldToneMapHdrToSdr)
        assertFalse(ProxySettings(sourceIsHdr = true, forceSdr = false).shouldToneMapHdrToSdr)
    }
}
