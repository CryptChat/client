package cc.osama.cryptchat.db

import androidx.annotation.NonNull
import androidx.room.*

@Entity(tableName = "messages")
data class Message(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  @NonNull val serverId: Long,
  @NonNull val userId: Long,
  @NonNull var status: Int,
  val plaintext: String,
  var senderPublicEphemeralKey: String? = null,
  var receiverEphemeralKeyPairId: Long? = null,
  @NonNull val createdAt: Long
) {
  companion object {
    val PENDING = 1
    val SENT = 2

    val RECEIVED = 100
    val DECRYPTION_FAILED = 101
    val BAD_MAC = 102
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

    @Query("SELECT * FROM messages WHERE serverId = :serverId AND userId = :userId ORDER BY createdAt")
    fun findByServerAndUser(serverId: Long, userId: Long) : List<Message>
  }
}