package com.myvitory.app.perf

import android.app.ActivityManager
import android.provider.Settings
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class DevicePerformanceTest {
    private val context get() = RuntimeEnvironment.getApplication()

    @Before
    @After
    fun reset() = DevicePerformance.override(null)

    @Test
    fun `animações do sistema zeradas desligam todo movimento`() {
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        assertEquals(MotionLevel.Off, DevicePerformance.motionLevel(context))
    }

    @Test
    fun `celular com pouca memória fica no nível leve`() {
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        shadowOf(context.getSystemService(ActivityManager::class.java)).setIsLowRamDevice(true)
        assertEquals(MotionLevel.Lite, DevicePerformance.motionLevel(context))
        assertTrue(DevicePerformance.isLowEnd(context))
    }
}
