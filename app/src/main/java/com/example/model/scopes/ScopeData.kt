package com.example.model.scopes

data class ScopeData(
    val histogramR: IntArray = IntArray(256),
    val histogramG: IntArray = IntArray(256),
    val histogramB: IntArray = IntArray(256),
    val histogramLuma: IntArray = IntArray(256),
    
    // 256 columns, 256 rows
    // stored flat: index = x * 256 + y
    val waveformLuma: IntArray = IntArray(256 * 256),
    val paradeR: IntArray = IntArray(256 * 256),
    val paradeG: IntArray = IntArray(256 * 256),
    val paradeB: IntArray = IntArray(256 * 256),
    
    // 256x256 grid for U/V (-128 to 127 mapped to 0 to 255)
    val vectorscope: IntArray = IntArray(256 * 256),
    
    val vectorscopeSkinTone: Boolean = false
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        return true
    }

    override fun hashCode(): Int = javaClass.hashCode()
}
