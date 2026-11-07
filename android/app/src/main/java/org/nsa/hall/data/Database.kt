package org.nsa.hall.data

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "games")
data class GameRecord(@PrimaryKey val id: String, val summary: String, val detail: String = "", val activeRelease: String = "", val favorite: Boolean = false, val lastPlayed: Long = 0, val settings: String = "{}")
@Entity(tableName = "transfers", indices = [Index("gameId"), Index(value = ["ownerGameId"], unique = true)])
data class TransferRecord(@PrimaryKey val id: String, val gameId: String, val releaseId: String, val manifest: String, val state: String = "queued", val intent: String = "run", val blockers: String = "[]", val bytes: Long = 0, val total: Long = 0, val failures: Int = 0, val error: String = "", val created: Long = System.currentTimeMillis(), val cellularApproved: Boolean = false, val experimentalApproved: Boolean = false,
    @ColumnInfo(defaultValue = "NULL") val ownerGameId: String? = if (state in listOf("completed", "cancelled")) null else gameId,
    @ColumnInfo(defaultValue = "0") val retryAt: Long = 0)
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
    @Query("UPDATE games SET settings=:settings WHERE id=:id") suspend fun gameSettings(id: String, settings: String)
    @Query("UPDATE games SET lastPlayed=:time WHERE id=:id") suspend fun gamePlayed(id: String, time: Long)
    @Query("UPDATE games SET activeRelease=:release WHERE id=:id") suspend fun gameRelease(id: String, release: String)
    @Query("SELECT * FROM transfers ORDER BY created, id") fun transfers(): Flow<List<TransferRecord>>
    @Query("SELECT * FROM transfers ORDER BY created, id") suspend fun allTransfers(): List<TransferRecord>
    @Query("SELECT * FROM transfers WHERE id=:id") suspend fun transfer(id: String): TransferRecord?
    @Query("SELECT * FROM transfers WHERE ownerGameId=:game") suspend fun activeTransfer(game: String): TransferRecord?
    @Upsert suspend fun putTransfer(transfer: TransferRecord)
    // Executors never write a stale entity back over a newer user command.
    @Query("UPDATE transfers SET state=:state, bytes=:bytes, blockers=:blockers, error=:error WHERE id=:id AND intent='run' AND ownerGameId=gameId AND state NOT IN ('completed','cancelled','failed')")
    suspend fun transferProgress(id: String, state: String, bytes: Long, blockers: String = "[]", error: String = ""): Int
    @Query("UPDATE transfers SET intent='pause', state='paused' WHERE id=:id AND ownerGameId=gameId AND intent!='cancel' AND state NOT IN ('completed','cancelled')") suspend fun pauseTransfer(id: String): Int
    @Query("UPDATE transfers SET intent='run', state='queued', failures=0, retryAt=0, error='', blockers='[]' WHERE id=:id AND ownerGameId=gameId AND intent!='cancel' AND state NOT IN ('completed','cancelled')") suspend fun resumeTransfer(id: String): Int
    @Query("UPDATE transfers SET intent='cancel', state='waiting', blockers='[\"cancellation\"]' WHERE id=:id AND state NOT IN ('completed','cancelled')") suspend fun cancelTransfer(id: String): Int
    @Query("UPDATE transfers SET cellularApproved=1 WHERE id=:id AND ownerGameId=gameId AND intent!='cancel' AND state NOT IN ('completed','cancelled')") suspend fun approveCellular(id: String): Int
    @Query("UPDATE transfers SET failures=MIN(failures+1,5), retryAt=:retryAt, state=CASE WHEN failures+1>=5 OR :permanent THEN 'failed' ELSE 'waiting' END, error=:error WHERE id=:id AND intent='run' AND ownerGameId=gameId AND state NOT IN ('completed','cancelled','failed')")
    suspend fun transferFailure(id: String, error: String, permanent: Boolean = false, retryAt: Long = 0): Int
    @Query("UPDATE transfers SET state='completed', bytes=total, ownerGameId=NULL, blockers='[]', error='' WHERE id=:id AND state!='cancelled'") suspend fun completeTransfer(id: String): Int
    @Query("UPDATE transfers SET state='cancelled', bytes=0, ownerGameId=NULL, blockers='[]', error='' WHERE id=:id AND intent='cancel' AND state!='completed'") suspend fun acknowledgeCancellation(id: String): Int
    @Query("UPDATE transfers SET bytes=:bytes, state=CASE WHEN intent='cancel' THEN 'waiting' WHEN intent='pause' THEN 'paused' WHEN state='failed' THEN 'failed' ELSE 'queued' END WHERE id=:id AND state NOT IN ('completed','cancelled')") suspend fun reconcileTransfer(id: String, bytes: Long): Int
    @Query("UPDATE transfers SET error=:error WHERE id=:id") suspend fun transferNotice(id: String, error: String)
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
    @Query("SELECT * FROM facts WHERE `key` LIKE 'install-recovery:%'") suspend fun installRecoveryFacts(): List<Fact>
    @Upsert suspend fun putFact(fact: Fact)
    @Query("DELETE FROM facts WHERE `key`=:key") suspend fun deleteFact(key: String)
}

@Database(entities = [GameRecord::class, TransferRecord::class, InstallRecord::class, SessionRecord::class, Fact::class], version = 2, exportSchema = true)
abstract class HallDatabase : RoomDatabase() {
    abstract fun dao(): HallDao
    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transfers ADD COLUMN ownerGameId TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE transfers ADD COLUMN retryAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE transfers SET ownerGameId=gameId WHERE state NOT IN ('completed','cancelled') AND id=(SELECT t.id FROM transfers t WHERE t.gameId=transfers.gameId AND t.state NOT IN ('completed','cancelled') ORDER BY t.created,t.id LIMIT 1)")
                // Preserve all old files; the executor acknowledges cleanup later.
                db.execSQL("UPDATE transfers SET intent='cancel',state='waiting',blockers='[\"cancellation\"]',error='旧重复任务等待清理；原文件保留至清理完成' WHERE state NOT IN ('completed','cancelled') AND ownerGameId IS NULL")
                db.execSQL("CREATE UNIQUE INDEX index_transfers_ownerGameId ON transfers(ownerGameId)")
            }
        }
        fun open(context: Context) = Room.databaseBuilder(context, HallDatabase::class.java, "hall.db").addMigrations(MIGRATION_1_2).build()
    }
}
