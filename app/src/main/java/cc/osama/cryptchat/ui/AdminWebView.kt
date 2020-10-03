package cc.osama.cryptchat.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log.d
import android.util.TypedValue
import android.view.MenuItem
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import cc.osama.cryptchat.*
import cc.osama.cryptchat.db.Server
import kotlinx.android.synthetic.main.activity_admin_web_view.*

class AdminWebView: AppCompatActivity() {
  companion object {
    private const val ADMIN_TOKEN_HEADER = "Cryptchat-Admin-Token"
    private const val ADMIN_ID_HEADER = "Cryptchat-Admin-Id"
    fun createIntent(server: Server, context: Context) : Intent {
      return Intent(context, AdminWebView::class.java).apply {
        putExtra("server", server)
      }
    }
  }

  private lateinit var server: Server

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_admin_web_view)
    server = intent.extras?.get("server") as Server
    TypedValue().also {
      theme.resolveAttribute(R.attr.colorSecondary, it, true)
      adminWebViewToolbar.navigationIcon?.setTint(it.data)
    }
    setSupportActionBar(adminWebViewToolbar)
    supportActionBar?.setDisplayHomeAsUpEnabled(true)
    adminWebview.webChromeClient = object : WebChromeClient() {
      override fun onProgressChanged(view: WebView?, newProgress: Int) {
        super.onProgressChanged(view, newProgress)
        webViewProgressBar.progress = newProgress
      }
    }
    adminWebview.webViewClient = object : WebViewClient() {
      override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        val url = request?.url?.toString()
        return if (url != null && url.startsWith(server.address)) {
          false
        } else {
          super.shouldOverrideUrlLoading(view, request)
        }
      }
      override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        adminWebViewToolbar.subtitle = url
        webViewProgressBar.visibility = View.VISIBLE
      }
      override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        webViewProgressBar.visibility = View.GONE
      }
    }
    AsyncExec.run(AsyncExec.Companion.Threads.Network) {
      server.reload(applicationContext)
      CryptchatServer(applicationContext, server).request(
        CryptchatRequest.Methods.POST,
        "/generate-admin-token.json",
        async = false,
        success = { json ->
          val key = CryptchatUtils.jsonOptString(json, "key")
          if (key != null) {
            AsyncExec.onUiThread {
              adminWebview.loadUrl(
                server.urlForPath("/admin"),
                mapOf(
                  Pair(ADMIN_TOKEN_HEADER, key),
                  Pair(ADMIN_ID_HEADER, server.userId.toString())
                )
              )
            }
          } else {
            d("AdminWebView", "admin auth token was null. json=$json")
          }
        },
        failure = {
          d("AdminWebView", it.toString())
        }
      )
    }
  }

  override fun onBackPressed() {
    if (adminWebview.canGoBack()) {
      adminWebview.goBack()
    } else {
      super.onBackPressed()
    }
  }

  override fun onOptionsItemSelected(item: MenuItem): Boolean {
    if (item.itemId == android.R.id.home) {
      finish()
    } else {
      return super.onOptionsItemSelected(item)
    }
    return true
  }
}
