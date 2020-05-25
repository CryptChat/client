package cc.osama.cryptchat.db

import androidx.annotation.NonNull
import androidx.room.*
import cc.osama.cryptchat.ECKeyPair
import cc.osama.cryptchat.ECPrivateKey
import org.whispersystems.curve25519.Curve25519KeyPair

@Entity(tableName = "servers")
data class Server (
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  @NonNull val address: String,
  @NonNull val userId: Long,
  @NonNull val publicKey: String, // these 2 attributes should be private... but that would cause errors
  @NonNull val privateKey: String,
  var name: String?
) {
  @Ignore val keyPair = ECKeyPair(publicKey = publicKey, privateKey = privateKey)

  constructor(
    id: Long = 0,
    name: String?,
    address: String,
    userId: Long,
    keyPair: ECKeyPair
  ) : this(
    id = id,
    name = name,
    address = address,
    userId = userId,
    publicKey = keyPair.publicKey.toString(),
    privateKey = keyPair.privateKey.toString()
  )

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
