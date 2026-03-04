package nu.staldal.mycal.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PendingChangeDao {
    @Query("SELECT * FROM pending_changes ORDER BY timestamp ASC")
    suspend fun getAllChanges(): List<PendingChange>

    @Query("SELECT COUNT(*) FROM pending_changes")
    fun getPendingCount(): Flow<Int>

    @Insert
    suspend fun insert(change: PendingChange)

    @Delete
    suspend fun delete(change: PendingChange)

    @Query("DELETE FROM pending_changes WHERE eventId = :eventId")
    suspend fun deleteByEventId(eventId: String)

    @Query("DELETE FROM pending_changes WHERE eventId = :eventId AND changeType != 'DELETE'")
    suspend fun deleteNonDeleteByEventId(eventId: String)
}
