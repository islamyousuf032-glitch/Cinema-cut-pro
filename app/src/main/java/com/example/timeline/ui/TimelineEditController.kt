package com.example.timeline.ui

interface TimelineEditController {
    fun selectClip(clipId: String?)
    fun moveClip(clipId: String, newStartFrame: Long)
    fun trimClipStart(clipId: String, newStartFrame: Long)
    fun trimClipEnd(clipId: String, newEndFrame: Long)
    fun splitAtPlayhead()
    fun deleteSelectedClip()
}
