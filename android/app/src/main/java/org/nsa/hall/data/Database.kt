package org.nsa.hall.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "games")
data class GameRecord(@PrimaryKey val id: String, val summary: String, val detail: String = "", val activeRelease: String = "", val favorite: Boolean = false, val lastPlayed: Long = 0, val settings: String = "{}")
@Entity(tableName = "transfers", indices = [Index("gameId")])
data class TransferRecord(@PrimaryKey val id: String, val gameId: String, val releaseId: String, val manifest: String, val state: String = "queued", val intent: String = "run", val blockers: String = "[]", val bytes: Long = 0, val total: Long = 0, val failures: Int = 0, val error: String = "", val created: Long = System.currentTimeMillis(), val cellularApproved: Boolean = false, val experimentalApproved: Boolean = false)
@Entity(tableName = "installs", primaryKeys = ["gameId", "releaseId"])
data class InstallRecord(val gameId: String, val releaseId: String, val manifest: String, val state: String = "ready", val healthy: Boolean = false, val installed: Long = System.currentTimeMillis())
@Entity(tableName = "sessions")
data class SessionRecord(@PrimaryKey val id: String, val gameId: String, val releaseId: String, val generation: Int, val state: String, val seq: Long = 0, val saveSeq: Long = 0, val error: String = "", val started: Long = System.currentTimeMillis(), val updated: Long = System.currentTimeMillis())
@Entity(tableName = "facts")
data class Fact(@PrimaryKey val key: String, val value: String)

@Dao
interface HallDao {
    @Query("SELECT * FROM games ORDER BY lastPlayed DESC, id") fun games(): Flow<List<GameRecord>>
    @Query("SELECT * FROM games") suspend fun allGames(): List<GameRecord>
    @Query("SELECT * FROM games WHERE id=:id") suspend fun game(id: String): GameRecord?
    @Upsert suspend fun putGame(game: GameRecord)
    @Query("SELECT * FROM transfers ORDER BY created") fun transfers(): Flow<List<TransferRecord>>
    @Query("SELECT * FROM transfers ORDER BY created") suspend fun allTransfers(): List<TransferRecord>
    @Query("SELECT * FROM transfers WHERE id=:id") suspend fun transfer(id: String): TransferRecord?
    @Upsert suspend fun putTransfer(transfer: TransferRecord)
    @Query("DELETE FROM transfers WHERE id=:id") suspend fun deleteTransfer(id: String)
    @Query("SELECT * FROM installs WHERE gameId=:gameId AND releaseId=:releaseId") suspend fun install(gameId: String, releaseId: String): InstallRecord?
    @Query("SELECT * FROM installs WHERE gameId=:gameId ORDER BY installed DESC") suspend fun installs(gameId: String): List<InstallRecord>
    @Query("SELECT * FROM installs") fun installed(): Flow<List<InstallRecord>>
    @Query("SELECT * FROM installs") suspend fun allInstalls(): List<InstallRecord>
    @Upsert suspend fun putInstall(install: InstallRecord)
    @Query("DELETE FROM installs WHERE gameId=:gameId AND releaseId=:releaseId") suspend fun deleteInstall(gameId: String, releaseId: String)
    @Query("SELECT * FROM sessions ORDER BY started DESC LIMIT 1") fun latestSession(): Flow<SessionRecord?>
    @Query("SELECT * FROM sessions WHERE id=:id") suspend fun session(id: String): SessionRecord?
    @Query("SELECT * FROM sessions WHERE state NOT IN ('ended','failed','cancelled','crashed') ORDER BY started DESC") suspend fun unfinished(): List<SessionRecord>
    @Upsert suspend fun putSession(session: SessionRecord)
    @Query("SELECT * FROM facts WHERE `key`=:key") suspend fun fact(key: String): Fact?
    @Query("SELECT * FROM facts WHERE `key`=:key") fun observeFact(key: String): Flow<Fact?>
    @Upsert suspend fun putFact(fact: Fact)
    @Query("DELETE FROM facts WHERE `key`=:key") suspend fun deleteFact(key: String)
}

@Database(entities = [GameRecord::class, TransferRecord::class, InstallRecord::class, SessionRecord::class, Fact::class], version = 1, exportSchema = false)
abstract class HallDatabase : RoomDatabase() {
    abstract fun dao(): HallDao
    companion object {
        fun open(context: Context) = Room.databaseBuilder(context, HallDatabase::class.java, "hall.db").build()
    }
}
