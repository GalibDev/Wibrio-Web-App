package com.wibrio.mesh

/** Bounded per-session replay filter. Allows reordering within the last 64 packets. */
class ReplayWindow {
    private var highest = 0L
    private var bits = 0L
    fun accept(sequence: Long): Boolean {
        if (sequence <= 0) return false
        if (sequence > highest) {
            val advance = sequence - highest
            bits = if (advance >= 64) 1 else (bits shl advance.toInt()) or 1
            highest = sequence
            return true
        }
        val distance = highest - sequence
        if (distance >= 64) return false
        val mask = 1L shl distance.toInt()
        if (bits and mask != 0L) return false
        bits = bits or mask
        return true
    }
}
