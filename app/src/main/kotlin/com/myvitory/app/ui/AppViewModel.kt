package com.myvitory.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.myvitory.app.AppContainer
import com.myvitory.app.MyVitoryApp

/** Cria o ViewModel com os repositórios do app (injeção manual). */
@Composable
inline fun <reified VM : ViewModel> appViewModel(key: String? = null, crossinline create: (AppContainer) -> VM): VM {
    val app = LocalContext.current.applicationContext as MyVitoryApp
    return viewModel(key = key, factory = viewModelFactory { initializer { create(app.container) } })
}

/** Mostra uma mensagem com "Desfazer" no rodapé. */
fun interface UndoMessenger {
    fun show(message: String, onUndo: (() -> Unit)?)
}

val LocalUndo = staticCompositionLocalOf { UndoMessenger { _, _ -> } }
