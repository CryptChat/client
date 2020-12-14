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

  data class ServerListItem(
    val unreadMessagesCount: Int = 0,
    val lastActivity: Long?,
    val lastMessage: String?,
    val usersCount: Int = 0,
    @Embedded val server: Server
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
    }
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

    @Query("SELECT s.*, count.unreadMessagesCount, lastMessage.lastActivity, usersCount.usersCount FROM servers s LEFT JOIN (SELECT COUNT(*) unreadMessagesCount, serverId FROM messages WHERE NOT read AND (status < ${Message.UNDECRYPTED} OR status = ${Message.DECRYPTED}) GROUP BY serverId) count ON s.id = count.serverId LEFT JOIN (SELECT MAX(createdAt) lastActivity, serverId FROM messages WHERE (status < ${Message.UNDECRYPTED} OR status = ${Message.DECRYPTED}) GROUP BY serverId) lastMessage ON lastMessage.serverId = s.id LEFT JOIN (SELECT COUNT(*) usersCount, serverId FROM users GROUP BY serverId) usersCount ON usersCount.serverId = s.id ORDER BY lastMessage.lastActivity DESC")
    fun serversList() : List<ServerListItem>

    @Query("SELECT * FROM servers WHERE address = :address LIMIT 1")
    fun findByAddress(address: String): Server?

    @Query("SELECT * FROM servers WHERE senderId = :senderId")
    fun findBySenderId(senderId: String): List<Server>

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
