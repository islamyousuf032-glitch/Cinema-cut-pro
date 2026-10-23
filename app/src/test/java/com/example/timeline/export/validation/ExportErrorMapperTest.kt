package com.example.timeline.export.validation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportErrorMapperTest {
    @Test
    fun revokedDocumentTreeAccessSuggestsChoosingAnOutputFolderAgain() {
        val mapped = ExportErrorMapper.mapExceptionToError(SecurityException("permission revoked"))

        assertEquals(ExportErrorType.OUTPUT_NOT_WRITABLE, mapped.type)
        assertTrue(mapped.message.contains("Choose the folder again"))
        assertTrue(ExportErrorMapper.getRecoveryActions(mapped).contains(ExportRecoveryAction.CHOOSE_OUTPUT_FOLDER))
    }
}
