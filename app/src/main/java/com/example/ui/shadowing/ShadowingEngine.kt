package com.example.ui.shadowing

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object ShadowingEngine {
    private val _currentInput = MutableStateFlow<ShadowingInputContent?>(null)
    val currentInput: StateFlow<ShadowingInputContent?> = _currentInput.asStateFlow()

    fun launchEngine(input: ShadowingInputContent) {
        _currentInput.value = input
    }

    fun dismissEngine() {
        _currentInput.value = null
    }
}
