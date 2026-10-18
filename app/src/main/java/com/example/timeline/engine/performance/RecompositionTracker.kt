package com.example.timeline.engine.performance

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember

class RecompositionTracker {
    var count = 0
}

@Composable
fun rememberRecompositionTracker(): RecompositionTracker {
    val tracker = remember { RecompositionTracker() }
    SideEffect {
        tracker.count++
    }
    return tracker
}
