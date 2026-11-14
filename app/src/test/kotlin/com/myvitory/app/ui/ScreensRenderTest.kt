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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.myvitory.app.R
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

    /** O Selo V como o launcher desenha (fundo transparente) sobre papel de parede claro e escuro, mais o temático e a notificação. */
    @Test
    fun `renderiza o ícone do app`() {
        rule.setContent {
            Column(Modifier.testTag("icones").background(Color(0xFF888680)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    for (wallpaper in listOf(Color(0xFFE6E3DA), Color(0xFF2E3440), Color(0xFF4F7A5A))) {
                        Box(Modifier.size(96.dp).background(wallpaper), Alignment.Center) {
                            // Área visível do ícone adaptativo: 72 de 108, sem fundo.
                            Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.requiredSize(96.dp * 108 / 72))
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(96.dp).clip(CircleShape).background(Color(0xFF3D4A7A)), Alignment.Center) {
                        Icon(painterResource(R.drawable.ic_launcher_monochrome), null, Modifier.requiredSize(144.dp), tint = Color(0xFFD9E0FF))
                    }
                    Box(Modifier.size(48.dp).background(Color(0xFF111111)), Alignment.Center) {
                        Icon(painterResource(R.drawable.ic_notification), null, Modifier.size(24.dp), tint = Color.White)
                    }
                    Box(Modifier.size(24.dp).background(Color(0xFF111111)), Alignment.Center) {
                        Icon(painterResource(R.drawable.ic_notification), null, Modifier.size(16.dp), tint = Color.White)
                    }
                }
            }
        }
        val bitmap = rule.onNodeWithTag("icones").captureToImage().asAndroidBitmap()
        val out = File("build/outputs/render/icone.png").apply { parentFile?.mkdirs() }
        out.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        assertTrue(out.length() > 0)
    }

    private fun save(name: String) {
        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        val out = File("build/outputs/render/$name.png").apply { parentFile?.mkdirs() }
        out.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        assertTrue(out.length() > 0)
    }
}
