package cc.osama.cryptchat.db

import androidx.annotation.NonNull
import androidx.room.*
import cc.osama.cryptchat.ECPublicKey

@Entity(tableName = "users")
data class User(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  @NonNull val serverId: Long,
  @NonNull val idOnServer: Long,
  @NonNull val countryCode: String,
  @NonNull val phoneNumber: String,
  @NonNull val identityKey: String, // ideally this should be private...
  @NonNull val lastUpdatedAt: Double,
  val name: String?
) {
  @Ignore val publicKey = ECPublicKey(identityKey)

  constructor(
    id: Long = 0,
    serverId: Long,
    idOnServer: Long,
    countryCode: String,
    phoneNumber: String,
    publicKey: ECPublicKey,
    lastUpdatedAt: Double,
    name: String?
  ) : this(
    id = id,
    serverId = serverId,
    idOnServer = idOnServer,
    countryCode = countryCode,
    phoneNumber = phoneNumber,
    identityKey = publicKey.toString(),
    lastUpdatedAt = lastUpdatedAt,
    name = name
  )

  @Dao
  interface DataAccessObject {
    @Insert
    fun addMany(users: List<User>): List<Long>

    @Query("SELECT * FROM users")
    fun getAll(): List<User>
  }
}