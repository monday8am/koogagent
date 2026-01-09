package com.monday8am.koogagent.oauth

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.monday8am.koogagent.MainActivity

/**
 * Transparent activity that receives OAuth redirect callbacks. Immediately forwards the OAuth
 * response to MainActivity and finishes.
 */
class OAuthRedirectActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Forward the OAuth response to MainActivity
        val forwardIntent =
            Intent(this, MainActivity::class.java).apply {
                data = intent.data
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(EXTRA_OAUTH_REDIRECT, true)
            }
        startActivity(forwardIntent)
        finish()
    }

    companion object {
        const val EXTRA_OAUTH_REDIRECT = "oauth_redirect"
    }
}
