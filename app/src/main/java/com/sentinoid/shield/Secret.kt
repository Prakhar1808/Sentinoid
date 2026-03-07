package com.sentinoid.shield

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "secrets")
data class Secret(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val encryptedContent: ByteArray,
    val creationTimestamp: Long = System.currentTimeMillis()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Secret

        if (id != other.id) return false
        if (title != other.title) return false
        if (!encryptedContent.contentEquals(other.encryptedContent)) return false
        if (creationTimestamp != other.creationTimestamp) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id
        result = 31 * result + title.hashCode()
        result = 31 * result + encryptedContent.contentHashCode()
        result = 31 * result + creationTimestamp.hashCode()
        return result
    }
}