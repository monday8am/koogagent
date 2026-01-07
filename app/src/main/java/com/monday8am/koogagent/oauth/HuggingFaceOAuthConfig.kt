package com.monday8am.koogagent.oauth

/**
 * Configuration constants for HuggingFace OAuth integration.
 */
object HuggingFaceOAuthConfig {
    const val AUTHORIZATION_ENDPOINT = "https://huggingface.co/oauth/authorize"
    const val TOKEN_ENDPOINT = "https://huggingface.co/oauth/token"
    const val REDIRECT_URI = "koogagent://oauth/callback"
    const val SCOPE = "openid profile"
}
