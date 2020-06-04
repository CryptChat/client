package cc.osama.cryptchat.db

import androidx.annotation.NonNull
import androidx.lifecycle.LiveData
import androidx.room.*
import cc.osama.cryptchat.ECKeyPair

@Entity(tableName = "messages")
data class Message(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  @NonNull val serverId: Long,
  @NonNull val userId: Long,
  @NonNull var status: Int,
  var idOnServer: Long?,
  var plaintext: String,
  var senderPublicEphemeralKey: String? = null,
  var receiverEphemeralKeyPairId: Long? = null,
  @NonNull val createdAt: Long,
  @NonNull var read: Boolean
) {
  companion object {
    const val PENDING = 1
    const val SENT = 2

    const val UNDECRYPTED = 100
    const val DECRYPTED = 101
    const val DECRYPTION_FAILED = 102
    const val BAD_MAC = 103
    const val DELETED_EPHEMERAL_KEYPAIR = 104
    const val INCONSISTENT_STATE = 105
  }

  @Dao
  interface DataAccessObject {
    @Query("SELECT * FROM messages WHERE id = :id LIMIT 1")
    fun findById(id: Long): Message?

    @Insert
    fun addReturningId(message: Message) : Long

    @Insert
    fun add(message: Message) : Message {
      val id = addReturningId(message)
      return findById(id) as Message
    }

    @Update
    fun update(message: Message)

    @Query("SELECT * FROM messages WHERE serverId = :serverId AND userId = :userId AND id > :lastId ORDER BY createdAt")
    fun findByServerAndUserLive(serverId: Long, userId: Long, lastId: Long) : LiveData<List<Message>>

    @Query("SELECT * FROM messages WHERE serverId = :serverId AND userId = :userId ORDER BY createdAt")
    fun findByServerAndUser(serverId: Long, userId: Long) : List<Message>

    @Query("SELECT MAX(idOnServer) FROM messages WHERE serverId = :serverId AND status >= $UNDECRYPTED")
    fun findNewestReceivedMessageFromServer(serverId: Long) : Long?

    @Query("SELECT 1 FROM messages WHERE serverId = :serverId AND idOnServer = :idOnServer")
    fun checkMessageExists(serverId: Long, idOnServer: Long) : Boolean
  }
}