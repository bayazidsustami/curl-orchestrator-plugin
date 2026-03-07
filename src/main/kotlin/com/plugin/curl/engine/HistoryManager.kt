package com.plugin.curl.engine

import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.intellij.ide.util.PropertiesComponent

object HistoryManager {
    private const val HISTORY_KEY = "com.plugin.curl.history"
    private val gson = GsonBuilder().create()

    fun saveRequest(request: CurlRequest) {
        val history = getHistory().toMutableList()
        // Prevent exact duplicates
        history.removeAll { it.url == request.url && it.method == request.method && it.body == request.body }
        
        history.add(0, request) // Add to top
        if (history.size > 50) { // Limit to 50 items
            history.removeAt(history.size - 1)
        }
        
        val jsonString = gson.toJson(history)
        PropertiesComponent.getInstance().setValue(HISTORY_KEY, jsonString)
    }

    fun getHistory(): List<CurlRequest> {
        val jsonString = PropertiesComponent.getInstance().getValue(HISTORY_KEY)
        if (jsonString.isNullOrBlank()) {
            return emptyList()
        }
        return try {
            val type = object : TypeToken<List<CurlRequest>>() {}.type
            gson.fromJson(jsonString, type)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun clearHistory() {
        PropertiesComponent.getInstance().unsetValue(HISTORY_KEY)
    }
}
