package cc.osama.cryptchat.db

import androidx.annotation.NonNull
import androidx.room.*
import cc.osama.cryptchat.ECKeyPair
import java.io.Serializable

@Entity(tableName = "servers")
data class Server (
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  @NonNull val address: String,
  @NonNull val userId: Long, // user id on the server's database
  @NonNull val publicKey: String, // these 2 attributes should be private... but that would cause errors
  @NonNull val privateKey: String,
  @NonNull val senderId: String,
  @NonNull var authToken: String,
  var name: String?,
  var instanceId: String?
) : Serializable {
  @Ignore val keyPair = ECKeyPair(publicKey = publicKey, privateKey = privateKey)

  constructor(
    id: Long = 0,
    name: String?,
    address: String,
    userId: Long,
    keyPair: ECKeyPair,
    authToken: String,
    senderId: String,
    instanceId: String?
  ) : this(
    id = id,
    name = name,
    address = address,
    userId = userId,
    publicKey = keyPair.publicKey.toString(),
    privateKey = keyPair.privateKey.toString(),
    senderId = senderId,
    authToken = authToken,
    instanceId = instanceId
  )

  @Dao
  interface DataAccessObject {
    @Query("SELECT * FROM servers")
    fun getAll(): List<Server>

    @Query("SELECT * FROM servers WHERE address = :address LIMIT 1")
    fun findByAddress(address: String): Server?

    @Query("SELECT * FROM servers WHERE senderId = :senderId LIMIT 1")
    fun findBySenderId(senderId: String): Server?

    @Query("SELECT * FROM servers WHERE id = :id LIMIT 1")
    fun findById(id: Long): Server?

    @Insert
    fun addReturningId(server: Server) : Long

    @Update
    fun update(server: Server)

    @Insert
    fun add(server: Server) : Server {
      val id = addReturningId(server)
      return findById(id) as Server
    }
  }
}
