package com.wibrio.app

import androidx.room.Room
import com.wibrio.mesh.DeliveryState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.UUID
import javax.crypto.KeyGenerator

/** JVM Android/SQLite simulation, not hardware Keystore or real-device validation. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE, application = android.app.Application::class)
class RoomMessageJournalTest {
    @Test fun encryptedRoomSnapshotSurvivesDatabaseCloseAndReopen() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val name = "test-${UUID.randomUUID()}.db"
        val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val cipher = JournalCipher { key }
        val snapshot = MessageSnapshot(listOf(DirectMessage("id", "peer", "sensitive বাংলা", true, 1000, DeliveryState.QUEUED)), listOf(PendingMessage("id", "peer", "sensitive বাংলা", 1000, 61_000)), mapOf("peer:received" to 90_000))
        var db = Room.databaseBuilder(context, MessageDatabase::class.java, name).build()
        try {
            RoomMessageJournal(db, cipher).save(snapshot)
            val first = db.journal().read()!!.encrypted
            assertFalse(first.toString(Charsets.UTF_8).contains("sensitive"))
            RoomMessageJournal(db, cipher).save(snapshot)
            assertFalse(first.contentEquals(db.journal().read()!!.encrypted))
            db.close()
            db = Room.databaseBuilder(context, MessageDatabase::class.java, name).build()
            assertEquals(snapshot, RoomMessageJournal(db, cipher).load())
            val damaged = db.journal().read()!!.encrypted.clone(); damaged[damaged.lastIndex] = (damaged.last().toInt() xor 1).toByte()
            db.journal().write(JournalRow(encrypted = damaged))
            assertTrue(runCatching { RoomMessageJournal(db, cipher).load() }.isFailure)
            assertTrue(damaged.contentEquals(db.journal().read()!!.encrypted)) // Corruption never wipes history silently.
        } finally { db.close(); context.deleteDatabase(name) }
    }
}
