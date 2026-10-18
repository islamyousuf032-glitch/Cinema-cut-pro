package com.example

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.Content
import com.example.ai.GeminiRepository
import com.example.ai.Part
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ChatMessage(
    val id: String,
    val text: String,
    val isUser: Boolean,
    val isLoading: Boolean = false
)

class MainViewModel : ViewModel() {
    private val geminiRepository = GeminiRepository()

    private val _messages = MutableStateFlow<List<ChatMessage>>(listOf(
        ChatMessage("init", "Welcome to ChromaPro AI Colorist. Describe the look you're going for, and I'll analyze the best grading approach for your Rec.709 timeline using 'High Thinking'.", false)
    ))
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val chatHistory = mutableListOf<Content>()

    fun sendMessage(text: String) {
        if (text.isBlank()) return

        val userMessageId = java.util.UUID.randomUUID().toString()
        val userMsg = ChatMessage(userMessageId, text, isUser = true)
        
        val aiLoadingId = java.util.UUID.randomUUID().toString()
        val aiLoadingMsg = ChatMessage(aiLoadingId, "", isUser = false, isLoading = true)

        _messages.value = _messages.value + userMsg + aiLoadingMsg

        viewModelScope.launch {
            val response = geminiRepository.getColorGradingAdvice(text, chatHistory)
            
            // Update history
            chatHistory.add(Content(parts = listOf(Part(text = text)), role = "user"))
            chatHistory.add(Content(parts = listOf(Part(text = response)), role = "model"))

            _messages.value = _messages.value.map { msg ->
                if (msg.id == aiLoadingId) {
                    msg.copy(text = response, isLoading = false)
                } else {
                    msg
                }
            }
        }
    }
}
