package cc.osama.cryptchat.db

import androidx.annotation.NonNull
import androidx.room.*
import cc.osama.cryptchat.ECPublicKey
import java.io.Serializable

@Entity(tableName = "users")
data class User(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  @NonNull val serverId: Long,
  @NonNull val idOnServer: Long,
  @NonNull var countryCode: String,
  @NonNull var phoneNumber: String,
  @NonNull val identityKey: String, // ideally this should be private...
  @NonNull var lastUpdatedAt: Long,
  var name: String? = null
) : Serializable {
  @Ignore val publicKey = ECPublicKey(identityKey)

  data class Conversation(
    val lastMessage: String?,
    val lastMessageDate: Long?,
    val unreadCount: Int,
    @Embedded val user: User
  )

  constructor(
    id: Long = 0,
    serverId: Long,
    idOnServer: Long,
    countryCode: String,
    phoneNumber: String,
    publicKey: ECPublicKey,
    lastUpdatedAt: Long,
    name: String? = null
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

    @Query("SELECT * FROM users WHERE serverId = :serverId")
    fun findByServerId(serverId: Long): List<User>

    @Query("SELECT * FROM users WHERE serverId = :serverId AND idOnServer = :idOnServer LIMIT 1")
    fun findUserByServerIdAndIdOnServer(serverId: Long, idOnServer: Long): User?

    @Query("SELECT u.*, m.plaintext AS lastMessage, m.lastMessageDate AS lastMessageDate, COALESCE(c.unreadCount, 0) AS unreadCount FROM users u LEFT JOIN (SELECT MAX(createdAt) AS lastMessageDate, userId, plaintext FROM messages WHERE serverId = :serverId GROUP BY userId) m ON m.userId = u.id LEFT JOIN (SELECT COUNT(*) AS unreadCount, userId FROM messages WHERE serverId = :serverId AND read = 0 GROUP BY userId) c ON c.userId = u.id WHERE serverId = :serverId ORDER BY lastMessageDate DESC")
    fun findConversationsOnServer(serverId: Long) : List<Conversation>

    @Query("SELECT MAX(lastUpdatedAt) FROM users WHERE serverId = :serverId")
    fun findMaxLastUpdatedAtOnServer(serverId: Long) : Long?

    @Update
    fun update(user: User)
  }
}