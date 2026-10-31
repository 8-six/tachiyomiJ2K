package eu.kanade.tachiyomi.data.backup

import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import kotlinx.serialization.protobuf.ProtoBuf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BackupCategoryTest {
    private fun roundTrip(category: BackupCategory) =
        ProtoBuf.decodeFromByteArray(
            BackupCategory.serializer(),
            ProtoBuf.encodeToByteArray(BackupCategory.serializer(), category),
        )

    @Test
    fun `hierarchy, tracker status and schedule survive a round trip`() {
        val decoded = roundTrip(BackupCategory("Child", order = 2, parentName = "Parent", trackerStatus = 2, updateInterval = 24))
        assertEquals("Parent", decoded.parentName)
        assertEquals(2, decoded.getCategoryImpl().trackerStatus)
        assertEquals(24, decoded.getCategoryImpl().updateInterval)
    }

    @Test
    fun `categories from older backups are top level with no automation`() {
        val decoded = roundTrip(BackupCategory("Old"))
        assertNull(decoded.parentName)
        assertEquals(0, decoded.getCategoryImpl().trackerStatus)
        assertEquals(0, decoded.getCategoryImpl().updateInterval)
    }
}
