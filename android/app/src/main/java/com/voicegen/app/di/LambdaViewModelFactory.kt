package com.voicegen.app.di

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

/** A ViewModelProvider.Factory that just calls the given lambda — used per-screen with the shared AppContainer. */
class LambdaViewModelFactory<T : ViewModel>(private val create: () -> T) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <VM : ViewModel> create(modelClass: Class<VM>): VM = create() as VM
}
