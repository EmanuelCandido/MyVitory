package com.myvitory.app.ui

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.myvitory.app.data.db.ExerciseHistoryPoint
import com.myvitory.app.ui.exercise.EvolutionSection
import com.myvitory.app.ui.theme.MyVitoryTheme
import com.myvitory.app.ui.theme.Palette
import java.io.File
import java.time.LocalDate
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Renderiza o gráfico de evolução com dados de exemplo e salva a imagem para conferência visual. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w400dp-h800dp-xxhdpi")
class EvolutionChartRenderTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `renderiza o gráfico de evolução`() {
        val start = LocalDate.of(2026, 8, 3)
        val kgs = listOf(120.0, 120.0, 125.0, 125.0, 130.0, 130.0, 127.5, 135.0, 140.0, 140.0)
        val points = kgs.mapIndexed { i, kg -> ExerciseHistoryPoint(start.plusWeeks(i.toLong()), kg, kg * 30, "10, 10, 10") }
        rule.setContent {
            MyVitoryTheme {
                // Como no app: o fundo define a cor de texto padrão.
                Surface(color = Palette.Ground, modifier = Modifier.testTag("chart").width(380.dp)) {
                    EvolutionSection(points, Modifier.padding(16.dp))
                }
            }
        }
        val bitmap = rule.onNodeWithTag("chart").captureToImage().asAndroidBitmap()
        val out = File("build/outputs/render/evolucao.png").apply { parentFile?.mkdirs() }
        out.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        assertTrue(out.length() > 0)
    }
}
