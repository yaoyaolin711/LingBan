package me.rerere.rikkahub.data.device

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceShellExecutorTest {
    @Test
    fun allowsWhitelistedPrefixes() {
        assertTrue(DeviceShellExecutor.isCommandAllowed("am force-stop com.ss.android.ugc.aweme"))
        assertTrue(DeviceShellExecutor.isCommandAllowed("input keyevent 3"))
        assertTrue(DeviceShellExecutor.isCommandAllowed("pm list packages"))
    }

    @Test
    fun rejectsShellMetacharacters() {
        assertFalse(DeviceShellExecutor.isCommandAllowed("am force-stop foo; rm -rf /"))
        assertFalse(DeviceShellExecutor.isCommandAllowed("am start | cat"))
        assertFalse(DeviceShellExecutor.isCommandAllowed("echo \$HOME"))
        assertFalse(DeviceShellExecutor.isCommandAllowed("am start `id`"))
    }

    @Test
    fun rejectsUnknownCommands() {
        assertFalse(DeviceShellExecutor.isCommandAllowed("rm -rf /"))
        assertFalse(DeviceShellExecutor.isCommandAllowed("reboot"))
        assertEquals(false, DeviceShellExecutor.isCommandAllowed(""))
    }
}
