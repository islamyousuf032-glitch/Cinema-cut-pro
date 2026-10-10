package com.example.timeline.core

class SplitClipCommand(
    val clipId: String,
    val splitFrame: Long,
    val splitLinkedAudio: Boolean = true,
    override val commandName: String = "Split Clip",
    override val timestamp: Long = System.currentTimeMillis()
) : TimelineCommand {
    
    private var projectBefore: TimelineProject? = null
    private var projectAfter: TimelineProject? = null
    private var _affectedIds: List<String> = listOf(clipId)
    
    override val affectedClipIds: List<String> get() = _affectedIds

    override fun execute(project: TimelineProject): TimelineResult {
        projectBefore = project
        
        var currentProject = project
        val clipToSplit = currentProject.tracks.flatMap { it.clips }.find { it.id == clipId }
            ?: return TimelineResult(currentProject, "Clip not found")
            
        // First split the target clip
        val splitRes = CoreEditEngine.splitClipAtFrame(currentProject, clipId, splitFrame)
        if (!splitRes.isSuccess) {
            return TimelineResult(currentProject, splitRes.error)
        }
        
        currentProject = splitRes.project
        val allNewIds = splitRes.newClipIds.toMutableList()
        
        // If splitLinkedAudio is true, look for linked clips and split them too
        if (splitLinkedAudio && clipToSplit.mediaId.isNotEmpty()) {
            // A clip is considered linked if it has the same mediaId and overlaps the splitFrame
            // We just check if it's playing the exact same media at the same time.
            // Wait, typical linking in standard NLE: same media, same timelineStart and sourceIn
            val linkedClips = currentProject.tracks.flatMap { it.clips }
                .filter { it.id != clipId && it.id !in allNewIds && it.mediaId == clipToSplit.mediaId && it.timelineStart == clipToSplit.timelineStart && it.sourceIn == clipToSplit.sourceIn }
            
            for (linked in linkedClips) {
                val linkRes = CoreEditEngine.splitClipAtFrame(currentProject, linked.id, splitFrame)
                if (linkRes.isSuccess) {
                    currentProject = linkRes.project
                    allNewIds.addAll(linkRes.newClipIds)
                }
            }
        }
        
        projectAfter = currentProject
        _affectedIds = listOf(clipId) + allNewIds
        
        return TimelineResult(currentProject)
    }

    override fun undo(project: TimelineProject): TimelineResult {
        return TimelineResult(projectBefore ?: project)
    }

    override fun redo(project: TimelineProject): TimelineResult {
        return TimelineResult(projectAfter ?: project)
    }
}
