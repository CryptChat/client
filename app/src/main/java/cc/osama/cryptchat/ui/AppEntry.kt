package cc.osama.cryptchat.ui

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Looper
import android.util.Log.d
import android.util.Log.w
import cc.osama.cryptchat.*
import org.json.JSONObject

class AppEntry : AppCompatActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    // fileList().forEach {
    //   var file = getFileStreamPath(it)
    //   if (file.exists() && file.absolutePath.toLowerCase().indexOf("avatar") > -1) {
    //     file.delete()
    //   }
    // }
    // val store = AvatarsStore(1, 1, applicationContext)
    // AsyncExec.run {
    //   store.download("https://effigis.com/wp-content/themes/effigis_2014/img/RapidEye_RapidEye_5m_RGB_Altotting_Germany_Agriculture_and_Forestry_2009MAY17_8bits_sub_r_2.jpg", resources)
    //   store.bitmap(AvatarsStore.Companion.Sizes.Big)
    //   store.bitmap(AvatarsStore.Companion.Sizes.Small)
    //   fileList().forEach {
    //     var file = getFileStreamPath(it)
    //     if (file.exists() && file.absolutePath.toLowerCase().indexOf("avatar") > -1) {
    //       file.delete()
    //     }
    //   }
    // }
    // return
    // val server = Server(
    //   address = "http://10.0.2.2:3000",
    //   userId = 1,
    //   keyPair = CryptchatSecurity.genKeyPair(),
    //   authToken = "sas",
    //   senderId = "aSASSAD",
    //   instanceId = null,
    //   name = "dasd",
    //   userName = null
    // )
    // ServerSettings.createIntent(server, this).also { intent ->
    //   startActivity(intent)
    // }
    // return
    // Cryptchat.db(applicationContext).asyncExec({
      // w("USERRRR CRYPTCHAT", FirebaseInstanceId.getInstance().getToken("530989455642", "FCM"))
      // w("USERRRR SECHAT", FirebaseInstanceId.getInstance().getToken("108521922410", "FCM"))
    // })

    /** val senderIdKeyPair = ECKeyPair(
      "niPLt99JahABLoSBx3vZK7kUWCyrrsF0RcVE9GYl3QY=",
      "qPsfwm+sTovdsv1/LpzYYbHhYQo3/UDs8LltDy72amI="
    )
    val senderEphKeyPair = ECKeyPair(
      "XRAvrsGtmD1jOZECtWxcLSuLdzb0+z39AxTd1TzDi0I=",
      "GNoiVReevmNxx+2899XTf/aK98T/qHszI8UY+sA6TFQ="
    )

    val receiverIdKeyPair = ECKeyPair(
      "RaBn1dzEXYLk6RHWlletqLA/4wiWSKvVnbcMWJKxUms=",
      "QEqJ1ZRVsU5sF6V+WD9xpVqLFVN6j/9E5Q7KRyCufk4="
    )
    val receiverEphKeyPair = ECKeyPair(
      "1ODHZG1vgDvVKIIOtaT7yczTrd5RluQK49FP9h02HkY=",
      "QJX5IynS6BlyiYRYrJgNqGn1XT95iQKXjGhw8zaimGI="
    )

    val message = "THIS IS VERY SECRET"
    val res = CryptchatSecurity().encrypt(
      message = message,
      senderIdKeyPair = senderIdKeyPair,
      receiverIdPubKey = receiverIdKeyPair.publicKey,
      receiverEphPubKey = receiverEphKeyPair.publicKey
    )
    val aft = CryptchatSecurity().decrypt(
      CryptchatSecurity.DecryptionInput(
        iv = res.iv,
        mac = res.mac,
        ciphertext = res.ciphertext,
        senderIdPubKey = senderIdKeyPair.publicKey,
        senderEphPubKey = res.senderEphPubKey,
        receiverIdKeyPair = receiverIdKeyPair,
        receiverEphPriKey = receiverEphKeyPair.privateKey
      )
    )
    return
    **/
    Cryptchat.db(applicationContext).also { db ->
      AsyncExec.run {
        val servers = db.servers().getAll()
        val intent = if (servers.isEmpty()) {
          Intent(this, EnterServerAddress::class.java)
        } else {
          // ServerUsersList.createIntent(
          //   servers[0],
          //   this
          // )
          ServersList.createIntent(this)
        }
        it.execMainThread {
          startActivity(intent)
          finish()
        }
      }
    }
  }
}
