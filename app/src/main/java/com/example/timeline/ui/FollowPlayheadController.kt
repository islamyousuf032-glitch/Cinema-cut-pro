package com.example.timeline.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object FollowPlayheadController {
    private val _isFollowing = MutableStateFlow(true)
    val isFollowing: StateFlow<Boolean> = _isFollowing.asStateFlow()

    fun setFollowing(following: Boolean) {
        _isFollowing.value = following
    }
    
    fun toggle() {
        _isFollowing.value = !_isFollowing.value
    }
}
