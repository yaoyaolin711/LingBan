package me.rerere.rikkahub.ui.components.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IntFieldEditingTest {

    @Test
    fun filterDigits_stripsNonDigitsAndCapsLength() {
        assertEquals("123", IntFieldEditing.filterDigits("1a2b3"))
        assertEquals("123456789", IntFieldEditing.filterDigits("1234567890123", maxLen = 9))
        assertEquals("", IntFieldEditing.filterDigits(""))
    }

    @Test
    fun commitIfInRange_allowsEmptyDraftWithoutCommit() {
        assertNull(IntFieldEditing.commitIfInRange("", 1..240))
        assertNull(IntFieldEditing.commitIfInRange("abc", 1..240))
    }

    @Test
    fun commitIfInRange_rejectsOutOfRangeWhileTyping() {
        // Mimics proactive cooldown min=30: typing "4" of "45" must not snap to 30.
        assertNull(IntFieldEditing.commitIfInRange("4", 30..1440))
        assertEquals(45, IntFieldEditing.commitIfInRange("45", 30..1440))
        assertEquals(30, IntFieldEditing.commitIfInRange("30", 1..240))
    }

    @Test
    fun finalizeOnBlur_emptyRestoresCurrent() {
        val (display, commit) = IntFieldEditing.finalizeOnBlur("", current = 30, range = 1..240)
        assertEquals("30", display)
        assertNull(commit)
    }

    @Test
    fun finalizeOnBlur_coercesBelowMin() {
        val (display, commit) = IntFieldEditing.finalizeOnBlur("5", current = 180, range = 30..1440)
        assertEquals("30", display)
        assertEquals(30, commit)
    }

    @Test
    fun finalizeOnBlur_sameValueDoesNotRecommit() {
        val (display, commit) = IntFieldEditing.finalizeOnBlur("45", current = 45, range = 1..720)
        assertEquals("45", display)
        assertNull(commit)
    }
}
