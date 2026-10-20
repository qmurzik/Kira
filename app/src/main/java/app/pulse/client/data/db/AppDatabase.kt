package app.pulse.client.data.db

import android.content.Context
import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

// Cache-only database, one file per account. Rows hold compact JSON of the domain
// models, so the schema stays tiny and destructive migration is acceptable.

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey val peerId: Long,
    val sortMajor: Long,
    val sortMinor: Long,
    val data: String,
)

@Entity(
    tableName = "messages",
    primaryKeys = ["peerId", "id"],
    indices = [Index(value = ["peerId", "date"])],
)
data class MessageEntity(
    val peerId: Long,
    val id: Long,
    val cmid: Long,
    val date: Long,
    val data: String,
)

@Entity(tableName = "owners")
data class OwnerEntity(@PrimaryKey val id: Long, val data: String)

@Entity(tableName = "feed", primaryKeys = ["feed", "position"])
data class FeedEntity(
    val feed: String,
    val position: Int,
    val postKey: String,
    val data: String,
)

@Entity(tableName = "feed_keys")
data class FeedKeyEntity(@PrimaryKey val feed: String, val nextFrom: String?)

@Dao
abstract class ConversationDao {
    @Query("SELECT * FROM conversations ORDER BY sortMajor DESC, sortMinor DESC")
    abstract fun observeAll(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE peerId = :peerId")
    abstract fun observe(peerId: Long): Flow<ConversationEntity?>

    @Query("SELECT * FROM conversations WHERE peerId = :peerId")
    abstract suspend fun get(peerId: Long): ConversationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsert(items: List<ConversationEntity>)

    @Query("DELETE FROM conversations")
    abstract suspend fun clear()

    @Transaction
    open suspend fun replaceAll(items: List<ConversationEntity>) {
        clear()
        upsert(items)
    }
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE peerId = :peerId ORDER BY date DESC, id DESC LIMIT :limit")
    fun observe(peerId: Long, limit: Int): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE peerId = :peerId ORDER BY date DESC, id DESC LIMIT 1")
    suspend fun latest(peerId: Long): MessageEntity?

    @Query("SELECT COUNT(*) FROM messages WHERE peerId = :peerId AND id > 0")
    suspend fun countSynced(peerId: Long): Int

    @Query("SELECT * FROM messages WHERE peerId = :peerId AND id = :id")
    suspend fun get(peerId: Long, id: Long): MessageEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(items: List<MessageEntity>)

    @Query("DELETE FROM messages WHERE peerId = :peerId AND id IN (:ids)")
    suspend fun delete(peerId: Long, ids: List<Long>)

    @Query("DELETE FROM messages WHERE id IN (:ids)")
    suspend fun deleteAnyPeer(ids: List<Long>)

    @Query("DELETE FROM messages WHERE peerId = :peerId AND id > 0")
    suspend fun clearSynced(peerId: Long)
}

@Dao
interface OwnerDao {
    @Query("SELECT * FROM owners WHERE id IN (:ids)")
    suspend fun get(ids: List<Long>): List<OwnerEntity>

    @Query("SELECT * FROM owners WHERE id = :id")
    fun observe(id: Long): Flow<OwnerEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(items: List<OwnerEntity>)
}

@Dao
interface FeedDao {
    @Query("SELECT * FROM feed WHERE feed = :feed ORDER BY position ASC")
    fun paging(feed: String): PagingSource<Int, FeedEntity>

    @Query("SELECT MAX(position) FROM feed WHERE feed = :feed")
    suspend fun maxPosition(feed: String): Int?

    @Query("SELECT * FROM feed WHERE feed = :feed AND postKey = :key LIMIT 1")
    suspend fun find(feed: String, key: String): FeedEntity?

    @Query("SELECT * FROM feed WHERE postKey = :key")
    suspend fun findAll(key: String): List<FeedEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(items: List<FeedEntity>)

    @Query("DELETE FROM feed WHERE feed = :feed")
    suspend fun clear(feed: String)

    @Query("SELECT * FROM feed_keys WHERE feed = :feed")
    suspend fun key(feed: String): FeedKeyEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setKey(key: FeedKeyEntity)
}

@Database(
    entities = [
        ConversationEntity::class,
        MessageEntity::class,
        OwnerEntity::class,
        FeedEntity::class,
        FeedKeyEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun conversations(): ConversationDao
    abstract fun messages(): MessageDao
    abstract fun owners(): OwnerDao
    abstract fun feed(): FeedDao

    companion object {
        fun fileName(userId: Long) = "cache_$userId.db"

        fun open(context: Context, userId: Long): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, fileName(userId))
                .fallbackToDestructiveMigration()
                .build()
    }
}
