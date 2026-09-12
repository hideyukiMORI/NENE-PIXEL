package io.github.hideyukimori.nenepixel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.Dispatchers

internal class AppLanguageViewModel(
    storage: AppLanguageStorage,
) : ViewModel() {
    val controller = AppLanguageController(storage, viewModelScope)

    companion object {
        fun factory(application: Application): ViewModelProvider.Factory =
            viewModelFactory {
                initializer { AppLanguageViewModel(AndroidAppLanguageStorage(application, Dispatchers.IO)) }
            }
    }
}
