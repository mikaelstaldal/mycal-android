package nu.staldal.mycal.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class ChangeType {
    CREATE, UPDATE, DELETE
}

@Entity(tableName = "pending_changes")
data class PendingChange(
    @PrimaryKey(autoGenerate = true) val changeId: Long = 0,
    val eventId: String,
    val changeType: ChangeType,
    val timestamp: Long = System.currentTimeMillis(),
)
