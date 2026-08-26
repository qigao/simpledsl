package example.app

import androidx.room3.Dao
import androidx.room3.Database
import androidx.room3.Entity
import androidx.room3.Insert
import androidx.room3.PrimaryKey
import androidx.room3.Query
import androidx.room3.RoomDatabase

@Entity
data class StoredItem(
    @PrimaryKey val id: Long,
    val value: String,
)

@Dao
interface StoredItemDao {
    @Insert
    suspend fun insert(item: StoredItem)

    @Query("SELECT * FROM StoredItem")
    suspend fun all(): List<StoredItem>
}

@Database(entities = [StoredItem::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun storedItemDao(): StoredItemDao
}
