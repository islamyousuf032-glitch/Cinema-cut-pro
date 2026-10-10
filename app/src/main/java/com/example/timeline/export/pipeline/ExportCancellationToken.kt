package com.example.timeline.export.pipeline

import java.util.concurrent.atomic.AtomicBoolean

class ExportCancellationToken {
    private val isCancelled = AtomicBoolean(false)
    
    fun cancel() {
        isCancelled.set(true)
    }
    
    fun isCancelled(): Boolean = isCancelled.get()
}
