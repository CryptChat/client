package cc.osama.cryptchat.db

import androidx.annotation.NonNull
import androidx.room.*

@Entity(tableName = "ephemeral_keys")
data class EphemeralKey(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  @NonNull val serverId: Long,
  @NonNull val privateKey: String,
  @NonNull val publicKey: String
) {
  @Dao
  interface DataAccessObject {
    @Insert
    fun addMany(keys: List<EphemeralKey>): List<Long>

    @Query("SELECT * FROM ephemeral_keys WHERE id IN (:ids)")
    fun findByIds(ids: List<Long>): List<EphemeralKey>

    @Delete
    fun deleteMany(ids: List<EphemeralKey>)
  }
}