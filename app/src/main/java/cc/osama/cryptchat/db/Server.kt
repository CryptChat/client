package cc.osama.cryptchat.db

import android.content.Context
import androidx.annotation.NonNull
import androidx.room.*
import cc.osama.cryptchat.AsyncExec
import cc.osama.cryptchat.Cryptchat
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
  @NonNull var isAdmin: Boolean = false,
  var name: String?,
  var instanceId: String?,
  var userName: String?
) : Serializable {
  @Ignore val keyPair = ECKeyPair(publicKey = publicKey, privateKey = privateKey)
  @Ignore private var lastReloadedAt = System.currentTimeMillis()

  constructor(
    id: Long = 0,
    name: String?,
    address: String,
    userId: Long,
    keyPair: ECKeyPair,
    authToken: String,
    senderId: String,
    instanceId: String?,
    userName: String?,
    isAdmin: Boolean = false
  ) : this(
    id = id,
    name = name,
    address = address,
    userId = userId,
    publicKey = keyPair.publicKey.toString(),
    privateKey = keyPair.privateKey.toString(),
    senderId = senderId,
    authToken = authToken,
    instanceId = instanceId,
    userName = userName,
    isAdmin = isAdmin
  )

  fun displayName() : String {
    val serverName = name?.trim()
    return if (serverName != null && serverName.isNotEmpty()) {
      serverName
    } else {
      address
    }
  }

  fun reload(context: Context) {
    Cryptchat.db(context).also {
      val newCopy = it.servers().findById(this.id) ?: return@also
      authToken = newCopy.authToken
      name = newCopy.name
      instanceId = newCopy.instanceId
      userName = newCopy.userName
      isAdmin = newCopy.isAdmin
      lastReloadedAt = System.currentTimeMillis()
    }
  }

  fun shouldReload() : Boolean {
    return System.currentTimeMillis() - 1000 * 60 > lastReloadedAt
  }

  fun urlForPath(path: String) : String {
    return if (address.endsWith("/") || path.startsWith("/")) {
      address + path
    } else {
      "$address/$path"
    }
  }

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
