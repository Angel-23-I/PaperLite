package com.angel.paperlite.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "docs")
data class Doc(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val pageCount: Int,
)

@Dao
interface DocDao {
    @Query("SELECT * FROM docs ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<Doc>>

    @Query("SELECT * FROM docs WHERE id = :id")
    fun observe(id: Long): Flow<Doc?>

    @Query("SELECT * FROM docs WHERE id = :id")
    suspend fun get(id: Long): Doc?

    @Insert
    suspend fun insert(doc: Doc): Long

    @Update
    suspend fun update(doc: Doc)

    @Query("DELETE FROM docs WHERE id = :id")
    suspend fun delete(id: Long)
}

@Database(entities = [Doc::class], version = 1, exportSchema = false)
abstract class AppDb : RoomDatabase() {
    abstract fun dao(): DocDao

    companion object {
        @Volatile private var instance: AppDb? = null

        fun get(context: Context): AppDb = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDb::class.java, "paperlite.db")
                .build().also { instance = it }
        }
    }
}
