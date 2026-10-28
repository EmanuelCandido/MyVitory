package com.myvitory.app.domain

import java.time.Duration

object VideoLinks {
    private val idPattern = Regex("^[A-Za-z0-9_-]{11}$")
    private val patterns = listOf(
        Regex("""youtu\.be/([A-Za-z0-9_-]{11})"""),
        Regex("""[?&]v=([A-Za-z0-9_-]{11})"""),
        Regex("""youtube(?:-nocookie)?\.com/(?:shorts|embed|live|v)/([A-Za-z0-9_-]{11})"""),
    )

    /** Extrai o ID de 11 caracteres de um link do YouTube (ou aceita o próprio ID). */
    fun parseId(input: String): String? {
        val text = input.trim()
        if (idPattern.matches(text)) return text
        if (!text.contains("youtu")) return null
        return patterns.firstNotNullOfOrNull { it.find(text)?.groupValues?.get(1) }
    }

    /** Duração ISO 8601 da API ("PT5M40S") em segundos. */
    fun parseDuration(iso: String?): Int? =
        iso?.let { runCatching { Duration.parse(it).seconds.toInt() }.getOrNull() }

    fun formatDuration(seconds: Int): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }

    /** Termo de busca padrão de um exercício. */
    fun queryFor(exerciseName: String) = "$exerciseName execução correta"
}
