package com.myvitory.app.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import com.myvitory.app.ui.theme.MyVitoryTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Com a fonte do sistema em 200%, cada aba precisa: ter um título marcado como cabeçalho
 * e não ter nenhum elemento tocável sem nome para o TalkBack.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h740dp")
class AccessibilityTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `abas têm cabeçalho e todo toque tem rótulo, com fonte grande`() {
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                MyVitoryTheme { MyVitoryRoot() }
            }
        }
        rule.waitUntilAtLeastOneExists(hasText("Hoje") and hasClickAction(), timeoutMillis = 10_000)

        for (tab in listOf("Hoje", "Semana", "Recuperação", "Exercícios")) {
            rule.onNodeWithText(tab, useUnmergedTree = true).performClick()
            rule.waitForIdle()
            rule.waitUntilAtLeastOneExists(isHeading(), timeoutMillis = 10_000)
            val unlabeled = rule.onAllNodes(hasClickAction()).fetchSemanticsNodes().filterNot { it.hasLabel() }
            assertTrue("Elementos tocáveis sem rótulo na aba $tab: ${unlabeled.map { it.config }}", unlabeled.isEmpty())
        }
    }

    private fun SemanticsNode.hasLabel(): Boolean {
        val description = config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()
        val text = config.getOrNull(SemanticsProperties.Text).orEmpty()
        val editable = config.getOrNull(SemanticsProperties.EditableText)
        return description.any { it.isNotBlank() } || text.any { it.isNotBlank() } || editable != null
    }
}
