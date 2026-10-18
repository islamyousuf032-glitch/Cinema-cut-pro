package com.example.timeline.export.service

object ExportCancelController {
    var isCancelled: Boolean = false
        private set

    fun cancel() {
        isCancelled = true
    }

    fun reset() {
        isCancelled = false
    }
}
