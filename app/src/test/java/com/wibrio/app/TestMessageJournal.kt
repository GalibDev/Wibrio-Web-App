package com.wibrio.app

/** Tests only. Round-trips the durable format instead of sharing mutable coordinator state. */
class TestMessageJournal : MessageJournal {
    var bytes: ByteArray? = null
    var failWrites = false
    override suspend fun load() = bytes?.let(MessageSnapshotCodec::decode) ?: MessageSnapshot()
    override suspend fun save(snapshot: MessageSnapshot) {
        if (failWrites) throw java.io.IOException("test storage failure")
        bytes = MessageSnapshotCodec.encode(snapshot)
    }
}
