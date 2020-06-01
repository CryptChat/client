package cc.osama.cryptchat

import android.os.AsyncTask
import androidx.room.Database
import androidx.room.RoomDatabase
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
  abstract fun servers() : Server.DataAccessObject
  abstract fun ephemeralKeys() : EphemeralKey.DataAccessObject
  abstract fun users() : User.DataAccessObject
  abstract fun messages() : Message.DataAccessObject
}