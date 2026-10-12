package com.wibrio.app

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.room.*
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.inject.Inject
import javax.inject.Singleton

@Entity(tableName = "mesh_journal")
data class JournalRow(@PrimaryKey val id: Int = 1, val encrypted: ByteArray)
@Dao interface JournalDao {
    @Query("SELECT * FROM mesh_journal WHERE id = 1") suspend fun read(): JournalRow?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun write(row: JournalRow)
}
@Database(entities = [JournalRow::class], version = 1, exportSchema = false)
abstract class MessageDatabase : RoomDatabase() { abstract fun journal(): JournalDao }

/** Bounded encrypted snapshot; one SQLite transaction commits history, queue and dedup together. */
@Singleton class RoomMessageJournal internal constructor(private val database: MessageDatabase, private val cipher: JournalCipher) : MessageJournal {
    @Inject constructor(@ApplicationContext context: Context) : this(
        Room.databaseBuilder(context, MessageDatabase::class.java, "wibrio-messages.db").build(), JournalCipher { key() })
    override suspend fun load(): MessageSnapshot = database.journal().read()?.let { MessageSnapshotCodec.decode(cipher.decrypt(it.encrypted)) } ?: MessageSnapshot()
    override suspend fun save(snapshot: MessageSnapshot) { database.journal().write(JournalRow(encrypted = cipher.encrypt(MessageSnapshotCodec.encode(snapshot)))) }
    companion object {
        private const val ALIAS = "wibrio.journal.v1"
        private fun key(): SecretKey {
            val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            if (!store.containsAlias(ALIAS)) KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
                init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build())
            }.generateKey()
            return store.getKey(ALIAS, null) as SecretKey
        }
    }
}
