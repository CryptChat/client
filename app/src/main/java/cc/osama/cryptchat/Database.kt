package cc.osama.cryptchat

import androidx.room.*
import androidx.room.Database
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery
import cc.osama.cryptchat.db.EphemeralKey
import cc.osama.cryptchat.db.Message
import cc.osama.cryptchat.db.Server
import cc.osama.cryptchat.db.User

@Database(
  entities = [
    Server::class,
    EphemeralKey::class,
    User::class,
    Message::class
  ],
  version = 1
)

abstract class Database : RoomDatabase() {
  companion object {
    const val Name = "cryptchat-database"
  }

  abstract fun servers() : Server.DataAccessObject
  abstract fun ephemeralKeys() : EphemeralKey.DataAccessObject
  abstract fun users() : User.DataAccessObject
  abstract fun messages() : Message.DataAccessObject
  abstract fun raw() : RawDataAccessObject

  fun checkpoint() {
    raw().ssq(SimpleSQLiteQuery("pragma wal_checkpoint(full)"))
  }
}

@Dao
interface RawDataAccessObject {
  @RawQuery
  fun ssq(ssq: SimpleSQLiteQuery): Int
}