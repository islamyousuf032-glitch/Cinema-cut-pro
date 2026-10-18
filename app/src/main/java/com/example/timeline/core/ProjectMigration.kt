package com.example.timeline.core

object ProjectMigration {
    const val CURRENT_SCHEMA_VERSION = 1
    
    fun migrateProjectIfNeeded(jsonData: String, fromVersion: Int): String {
        var currentData = jsonData
        var currentVersion = fromVersion

        while (currentVersion < CURRENT_SCHEMA_VERSION) {
            currentData = migrateOnce(currentData, currentVersion)
            currentVersion++
        }

        return currentData
    }

    private fun migrateOnce(jsonData: String, version: Int): String {
        return when (version) {
            0 -> {
                // Example schema 0 to 1 migration:
                // jsonData.replace("\"oldField\"", "\"newField\"")
                jsonData
            }
            else -> jsonData
        }
    }
}
