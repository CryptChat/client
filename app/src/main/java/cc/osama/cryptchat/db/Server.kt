package cc.osama.cryptchat.db

import androidx.annotation.NonNull
import androidx.room.*

@Entity(tableName = "servers")
data class Server (
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  @NonNull val address: String,
  @NonNull val userId: Long,
  @NonNull val publicKey: String,
  @NonNull val privateKey: String,
  var name: String?
) {
  @Dao
  interface DataAccessObject {
    @Insert
    fun add(server: Server): Long

    @Query("SELECT * FROM servers")
    fun getAll(): List<Server>

    @Query("SELECT * FROM servers WHERE address = :address LIMIT 1")
    fun findByAddress(address: String): Server?
  }
}
