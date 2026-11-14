package com.myvitory.app.perf

import android.app.ActivityManager
import android.content.Context
import android.provider.Settings

/**
 * Quanto o aparelho aguenta de movimento (o mesmo critério de web/src/lib/device.ts no painel):
 * - [Off]: o usuário zerou as animações do sistema (Opções do desenvolvedor ou acessibilidade);
 * - [Lite]: celular de entrada (RAM baixa ou até 4 núcleos): só retorno de toque, sem carimbo, brilho nem pulso;
 * - [Full]: o resto.
 */
enum class MotionLevel { Full, Lite, Off }

object DevicePerformance {
    private const val LOW_RAM_BYTES = 3L * 1024 * 1024 * 1024

    @Volatile private var cached: MotionLevel? = null

    fun motionLevel(context: Context): MotionLevel = cached ?: detect(context.applicationContext).also { cached = it }

    /** Celular fraco: usado também para o cache de imagens. */
    fun isLowEnd(context: Context): Boolean = motionLevel(context) != MotionLevel.Full && hardwareIsLow(context.applicationContext)

    private fun detect(context: Context): MotionLevel {
        val scale = runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        }.getOrDefault(1f)
        if (scale == 0f) return MotionLevel.Off
        return if (hardwareIsLow(context)) MotionLevel.Lite else MotionLevel.Full
    }

    private fun hardwareIsLow(context: Context): Boolean {
        val manager = context.getSystemService(ActivityManager::class.java) ?: return false
        val memory = ActivityManager.MemoryInfo().also(manager::getMemoryInfo)
        return manager.isLowRamDevice || memory.totalMem in 1 until LOW_RAM_BYTES || Runtime.getRuntime().availableProcessors() <= 4
    }

    /** Só para testes: força um nível. */
    fun override(level: MotionLevel?) {
        cached = level
    }
}
