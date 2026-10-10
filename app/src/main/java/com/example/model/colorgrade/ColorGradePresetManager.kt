package com.example.model.colorgrade

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

object ColorGradePresetManager {
    
    private val _presets = MutableStateFlow<List<PresetCategory>>(emptyList())
    val presets: StateFlow<List<PresetCategory>> = _presets.asStateFlow()

    init {
        loadDefaultPresets()
    }

    private fun loadDefaultPresets() {
        val userCategory = PresetCategory("User", mutableListOf())
        val cinematicCategory = PresetCategory("Cinematic", mutableListOf(
            ColorGradePreset(
                name = "Teal & Orange",
                description = "Classic blockbuster look",
                stack = ColorGradeStack(
                    targetType = ColorGradeTarget.CLIP,
                    targetId = "",
                    primaryCorrections = ColorGradeParams(
                        temperature = -0.1f, // Colder shadows
                        contrast = 0.2f
                    )
                )
            ),
            ColorGradePreset(
                name = "Moody Film",
                description = "High contrast, desaturated",
                stack = ColorGradeStack(
                    targetType = ColorGradeTarget.CLIP,
                    targetId = "",
                    primaryCorrections = ColorGradeParams(
                        contrast = 0.3f,
                        saturation = 0.7f
                    )
                )
            )
        ))
        val logCategory = PresetCategory("LOG conversion", mutableListOf(
            ColorGradePreset(
                name = "S-Log3 to Rec.709",
                description = "Default Sony S-Log3 conversion",
                stack = ColorGradeStack(
                    targetType = ColorGradeTarget.CLIP,
                    targetId = "",
                    inputTransform = LogTransformParams(
                        inputColorSpace = "S-Gamut3.Cine",
                        inputGamma = "S-Log3",
                        outputColorSpace = "Rec.709"
                    )
                )
            )
        ))
        val utilityCategory = PresetCategory("Utility", mutableListOf())

        _presets.value = listOf(userCategory, cinematicCategory, logCategory, utilityCategory)
    }

    fun savePreset(name: String, description: String, stack: ColorGradeStack, categoryName: String = "User") {
        val currentCategories = _presets.value.toMutableList()
        val categoryIndex = currentCategories.indexOfFirst { it.name == categoryName }
        val category = if (categoryIndex != -1) {
            currentCategories[categoryIndex]
        } else {
            PresetCategory(categoryName, mutableListOf())
        }
        
        val newPreset = ColorGradePreset(
            presetId = UUID.randomUUID().toString(),
            name = name,
            description = description,
            stack = stack.copy(
                stackId = UUID.randomUUID().toString(),
                targetId = "",
                targetType = ColorGradeTarget.CLIP
            )
        )
        
        val updatedPresets = category.presets.toMutableList().apply { add(newPreset) }
        val updatedCategory = category.copy(presets = updatedPresets)
        
        if (categoryIndex != -1) {
            currentCategories[categoryIndex] = updatedCategory
        } else {
            currentCategories.add(updatedCategory)
        }
        _presets.value = currentCategories
    }

    data class PresetCategory(
        val name: String,
        val presets: List<ColorGradePreset>
    )
}
