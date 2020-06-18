package cc.osama.cryptchat.db

import androidx.annotation.NonNull
import androidx.room.*
import cc.osama.cryptchat.ECKeyPair

@Entity(tableName = "ephemeral_keys")
data class EphemeralKey(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  @NonNull val serverId: Long,
  @NonNull val privateKey: String,
  @NonNull val publicKey: String
) {
  @Ignore val keyPair: ECKeyPair = ECKeyPair(publicKey = publicKey, privateKey = privateKey)

  @Dao
  interface DataAccessObject {
    @Insert
    fun addMany(keys: List<EphemeralKey>): List<Long>

    @Query("SELECT * FROM ephemeral_keys WHERE id IN (:ids)")
    fun findByIds(ids: List<Long>): List<EphemeralKey>

    @Query("SELECT * FROM ephemeral_keys WHERE id = :id")
    fun findById(id: Long): EphemeralKey?

    @Delete
    fun deleteMany(keys: List<EphemeralKey>)

    @Delete
    fun delete(keyId: Long)
  }
}