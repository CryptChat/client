package cc.osama.cryptchat.db

import androidx.annotation.NonNull
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "messages")
data class Message(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  @NonNull val serverId: Long,
  @NonNull val userId: Long,
  @NonNull val status: Int,
  @NonNull val ciphertext: String,
  @NonNull val mac: String,
  @NonNull val iv: String,
  val plaintext: String?,
  val senderPublicEphemeralKey: String?,
  val receiverEphemeralKeyPairId: Long?
) {
  companion object {
    val SENT = 1
    val RETRY = 2

    val RECEIVED = 100
    val DECRYPTION_FAILED = 101
    val BAD_MAC = 102
  }

  @Dao
  interface DataAcessObject {

  }
}