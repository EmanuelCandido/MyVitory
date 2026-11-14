package com.myvitory.app.ui

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import com.myvitory.app.ui.theme.MyVitoryTheme
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renderiza as telas principais com o plano de exemplo e salva em build/outputs/render, para conferir o
 * Sistema Adesivo sem celular. Só confere que cada tela abriu; o resto é olho.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w392dp-h860dp-xxhdpi")
class ScreensRenderTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @OptIn(ExperimentalTestApi::class)
    private fun openApp() {
        rule.setContent { MyVitoryTheme { MyVitoryRoot() } }
        rule.waitUntilAtLeastOneExists(hasText("Hoje") and hasClickAction(), timeoutMillis = 10_000)
        // Espera o card do dia (treino, descanso ou feito) sair do carregamento.
        rule.waitUntilAtLeastOneExists(isHeading(), timeoutMillis = 10_000)
        rule.waitForIdle()
    }

    @Test
    fun `renderiza as abas principais`() {
        openApp()
        save("hoje")
        for ((tab, file) in listOf("Semana" to "semana", "Recuperação" to "recuperacao", "Exercícios" to "exercicios", "Peso" to "peso")) {
            rule.onAllNodes(hasText(tab) and hasClickAction()).onFirst().performClick()
            rule.waitForIdle()
            save(file)
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `renderiza o treino com o descanso correndo`() {
        openApp()
        val start = hasText("COMEÇAR TREINO") and hasClickAction()
        if (rule.onAllNodes(start).fetchSemanticsNodes().isEmpty()) return // hoje sem treino no plano de exemplo
        // Clique pela semântica: na tela renderizada o botão fica atrás da barra de navegação.
        rule.onAllNodes(start).onFirst().performSemanticsAction(SemanticsActions.OnClick)
        rule.waitUntilAtLeastOneExists(hasText("FINALIZAR"), timeoutMillis = 10_000)
        rule.waitForIdle()
        save("treino")
        rule.waitUntilAtLeastOneExists(hasContentDescription("Série 1", substring = true), timeoutMillis = 10_000)
        // O descanso conta em tempo real: daqui em diante o relógio do teste anda só quando mandamos.
        rule.mainClock.autoAdvance = false
        rule.onAllNodes(hasContentDescription("Série 1", substring = true)).onFirst().performSemanticsAction(SemanticsActions.OnClick)
        rule.mainClock.advanceTimeBy(1_500)
        save("treino-descanso")
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `renderiza a nova semana depois de um treino perdido`() {
        openApp()
        val skip = hasText("HOJE NÃO VAI DAR") and hasClickAction()
        if (rule.onAllNodes(skip).fetchSemanticsNodes().isEmpty()) return
        rule.onAllNodes(skip).onFirst().performSemanticsAction(SemanticsActions.OnClick)
        rule.waitUntilAtLeastOneExists(hasText("NOVA SEMANA"), timeoutMillis = 10_000)
        rule.waitUntilAtLeastOneExists(hasText("APLICAR") or hasText("PULAR TREINO PERDIDO"), timeoutMillis = 10_000)
        rule.waitForIdle()
        save("nova-semana")
    }

    private fun save(name: String) {
        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        val out = File("build/outputs/render/$name.png").apply { parentFile?.mkdirs() }
        out.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        assertTrue(out.length() > 0)
    }
}
