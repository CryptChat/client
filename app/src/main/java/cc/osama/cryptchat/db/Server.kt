package cc.osama.cryptchat.db

import androidx.annotation.NonNull
import androidx.room.*
import cc.osama.cryptchat.ECKeyPair
import java.io.Serializable

@Entity(tableName = "servers")
data class Server (
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  @NonNull val address: String,
  @NonNull val userId: Long,
  @NonNull val publicKey: String, // these 2 attributes should be private... but that would cause errors
  @NonNull val privateKey: String,
  @NonNull val secretToken: String,
  var name: String?
) : Serializable {
  @Ignore val keyPair = ECKeyPair(publicKey = publicKey, privateKey = privateKey)

  constructor(
    id: Long = 0,
    name: String?,
    address: String,
    userId: Long,
    keyPair: ECKeyPair,
    secretToken: String
  ) : this(
    id = id,
    name = name,
    address = address,
    userId = userId,
    publicKey = keyPair.publicKey.toString(),
    privateKey = keyPair.privateKey.toString(),
    secretToken = secretToken
  )

  @Dao
  interface DataAccessObject {
    @Query("SELECT * FROM servers")
    fun getAll(): List<Server>

    @Query("SELECT * FROM servers WHERE address = :address LIMIT 1")
    fun findByAddress(address: String): Server?

    @Query("SELECT * FROM servers WHERE id = :id LIMIT 1")
    fun findById(id: Long): Server?

    @Insert
    fun addReturningId(server: Server) : Long

    @Insert
    fun add(server: Server) : Server {
      val id = addReturningId(server)
      return findById(id) as Server
    }
  }
}
