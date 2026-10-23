package com.example.timeline.export

import com.example.timeline.export.model.mp4ExportDisplayName
import com.example.timeline.export.model.normalizeExportFileBaseName
import org.junit.Assert.assertEquals
import org.junit.Test

class ExportFileNameTest {
    @Test
    fun appendsMp4AndRemovesPathOrUnsafeCharacters() {
        assertEquals("My Edit_ Final.mp4", mp4ExportDisplayName("../My Edit: Final.mp4"))
    }

    @Test
    fun blankNamesFallBackToAnAppExportName() {
        assertEquals("CinemaCutPro_Export", normalizeExportFileBaseName("   "))
    }
}
