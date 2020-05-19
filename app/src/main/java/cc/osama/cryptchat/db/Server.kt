package cc.osama.cryptchat.db

import androidx.annotation.NonNull
import androidx.room.*

@Entity(tableName = "servers")
data class Server (
  @PrimaryKey(autoGenerate = true) val id: Int = 0,
  @NonNull var address: String,
  @NonNull var userId: Int,
  var name: String?
) {
  @Dao
  interface DataAccessObject {
    @Insert
    fun add(server: Server)

    @Query("SELECT * FROM servers")
    fun getAll(): List<Server>

    @Query("SELECT * FROM servers WHERE address = :address LIMIT 1")
    fun findByAddress(address: String): Server?
  }
}
