package com.bitse.geotreasure.data.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

/**
 * 打卡请求的本地留痕：断网/进程被杀后能凭同一条记录恢复结果，避免重复提交。
 * 状态：PENDING（已发出，服务端未回）→ CONFIRMED / REJECTED。
 */
@Entity(tableName = "check_in_records")
data class CheckInRecordEntity(
    @PrimaryKey val id: String,
    val landmarkId: String,
    val photoPath: String?,
    val createdAtMillis: Long,
    val status: String
)

@Dao
interface CheckInRecordDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: CheckInRecordEntity)

    @Query("SELECT * FROM check_in_records ORDER BY createdAtMillis DESC")
    fun observeAll(): Flow<List<CheckInRecordEntity>>

    @Query("SELECT * FROM check_in_records WHERE status = 'PENDING' ORDER BY createdAtMillis DESC")
    fun observePending(): Flow<List<CheckInRecordEntity>>

    @Query("UPDATE check_in_records SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: String, status: String)

    @Query("DELETE FROM check_in_records WHERE id = :id")
    suspend fun delete(id: String)
}

@Database(entities = [CheckInRecordEntity::class], version = 1, exportSchema = false)
abstract class GeoTreasureDatabase : RoomDatabase() {
    abstract fun checkInDao(): CheckInRecordDao
}
