package cc.osama.cryptchat.db

import androidx.annotation.NonNull
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey

@Entity
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
  }
}