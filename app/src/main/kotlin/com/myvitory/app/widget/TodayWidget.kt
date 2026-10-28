package com.myvitory.app.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.myvitory.app.MainActivity
import com.myvitory.app.MyVitoryApp
import com.myvitory.app.data.TrainingRepository
import com.myvitory.app.domain.DayState
import com.myvitory.app.domain.SessionStatus
import com.myvitory.app.domain.WeekLogic
import com.myvitory.app.notify.Notifications
import com.myvitory.app.ui.components.headerLabel
import com.myvitory.app.ui.components.long
import java.time.LocalDate
import kotlinx.coroutines.flow.first

/** O que o widget mostra, decidido fora da UI para dar para testar. */
data class WidgetContent(
    val date: LocalDate,
    val title: String,
    val subtitle: String,
    val action: WidgetAction,
    val sessionId: Long? = null,
)

enum class WidgetAction { START, CONTINUE, REPLAN, OPEN }

object WidgetContentLoader {
    suspend fun load(training: TrainingRepository): WidgetContent {
        val today = training.today()
        val weekStart = WeekLogic.weekStartOf(today)
        training.ensureWeek(weekStart)
        val week = training.observeWeek(weekStart).first()
        val day = week.first { it.date == today }
        val next = week.firstOrNull { it.date.isAfter(today) && it.session?.status == SessionStatus.PLANNED }
        val session = day.session
        val nextLine = next?.let { "Próximo: ${it.date.dayOfWeek.long().lowercase()} · ${it.session?.name}" } ?: "Sem mais treinos nesta semana"
        return when {
            session == null || day.state == DayState.REST -> WidgetContent(today, "Descanso", nextLine, WidgetAction.OPEN)
            session.status == SessionStatus.PLANNED && day.state != DayState.UNAVAILABLE -> WidgetContent(
                today,
                session.name,
                "${day.exerciseCount} exercícios",
                if (session.startedAt != null) WidgetAction.CONTINUE else WidgetAction.START,
                session.id,
            )
            session.status == SessionStatus.DONE || session.status == SessionStatus.PARTIAL ->
                WidgetContent(today, "${session.name} ✓", nextLine, WidgetAction.OPEN)
            else -> WidgetContent(today, session.name, "Não vai dar hoje · ${nextLine.lowercase()}", WidgetAction.REPLAN)
        }
    }
}

/** Widget "Treino de hoje": o que fazer agora, e um toque para começar. */
class TodayWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.applicationContext as MyVitoryApp
        val content = WidgetContentLoader.load(app.container.training)
        provideContent { WidgetBody(context, content) }
    }

    companion object {
        /** Atualiza todos os widgets. Falhar aqui nunca pode derrubar o app. */
        suspend fun refresh(context: Context) {
            runCatching { TodayWidget().updateAll(context) }
        }
    }
}

class TodayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayWidget()
}

private val Ground = Color(0xFF0E0F10)
private val Lime = Color(0xFFCDF24B)
private val Text = Color(0xFFF3F2EE)
private val Muted = Color(0xFFA3A39E)

@Composable
private fun WidgetBody(context: Context, content: WidgetContent) {
    val open = intent(context, Notifications.OPEN_TODAY, null)
    Column(
        GlanceModifier
            .fillMaxSize()
            .background(Ground)
            .cornerRadius(20.dp)
            .padding(16.dp)
            .clickable(actionStartActivity(open)),
    ) {
        Text(content.date.headerLabel(), style = TextStyle(color = ColorProvider(Muted), fontSize = 11.sp, fontWeight = FontWeight.Medium))
        Text(content.title, maxLines = 1, style = TextStyle(color = ColorProvider(Text), fontSize = 22.sp, fontWeight = FontWeight.Bold))
        Text(content.subtitle, maxLines = 2, style = TextStyle(color = ColorProvider(Muted), fontSize = 13.sp))
        Spacer(GlanceModifier.defaultWeight())
        val label = when (content.action) {
            WidgetAction.START -> "Começar treino"
            WidgetAction.CONTINUE -> "Continuar treino"
            WidgetAction.REPLAN -> "Reorganizar a semana"
            WidgetAction.OPEN -> null
        }
        if (label != null) {
            val target = when (content.action) {
                WidgetAction.START, WidgetAction.CONTINUE -> intent(context, Notifications.OPEN_WORKOUT, content.sessionId)
                else -> open
            }
            Spacer(GlanceModifier.height(8.dp))
            Box(
                GlanceModifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .background(Lime)
                    .cornerRadius(22.dp)
                    .clickable(actionStartActivity(target)),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = TextStyle(color = ColorProvider(Ground), fontSize = 15.sp, fontWeight = FontWeight.Bold))
            }
        }
    }
}

private fun intent(context: Context, open: String, sessionId: Long?): Intent =
    Intent(context, MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        .putExtra(Notifications.EXTRA_OPEN, open)
        .apply { sessionId?.let { putExtra(Notifications.EXTRA_SESSION, it) } }
