package com.sentinoid.shield

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SecretViewModel(private val repository: SecretRepository) : ViewModel() {

    val allSecrets: StateFlow<List<Secret>> = repository.getAllSecrets()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun insert(title: String, content: String) = viewModelScope.launch {
        // TODO: Re-implement encryption with C++ core
        val encryptedContent = content.toByteArray() // Storing unencrypted for now to fix build
        repository.insert(Secret(title = title, encryptedContent = encryptedContent))
    }

    fun delete(id: Int) = viewModelScope.launch {
        repository.delete(id)
    }
}

class SecretViewModelFactory(private val repository: SecretRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SecretViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SecretViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}