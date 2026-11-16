package io.github.byjoelsamuel.drivesanity

import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RecordingKeepTest {
    private val ctx get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    @After
    fun stop() {
        if (DriveEngine.recording) DriveEngine.toggleRecording(ctx)
    }

    @Test
    fun aRecordingStoppedStraightAwayIsNotKept() {
        DriveEngine.toggleRecording(ctx)
        val file = DriveEngine.recordingFile!!
        ShadowSystemClock.advanceBy(Duration.ofSeconds(4))
        DriveEngine.toggleRecording(ctx)
        assertFalse(DriveEngine.lastRecordingKept)
        assertFalse("the empty file is removed", file.exists())
    }

    @Test
    fun aRealDriveIsKept() {
        DriveEngine.toggleRecording(ctx)
        val file = DriveEngine.recordingFile!!
        ShadowSystemClock.advanceBy(Duration.ofSeconds(20))
        DriveEngine.toggleRecording(ctx)
        assertTrue(DriveEngine.lastRecordingKept)
        assertTrue(file.exists())
        file.delete()
    }
}
