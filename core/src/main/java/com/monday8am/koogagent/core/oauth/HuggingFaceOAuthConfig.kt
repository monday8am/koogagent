package com.monday8am.koogagent.core.oauth

/** Configuration constants for HuggingFace OAuth integration. */
object HuggingFaceOAuthConfig {
    const val AUTHORIZATION_ENDPOINT = "https://huggingface.co/oauth/authorize"
    const val TOKEN_ENDPOINT = "https://huggingface.co/oauth/token"
    const val SCOPE = "openid profile"
    const val EXTRA_OAUTH_REDIRECT = "oauth_redirect"

    /** Creates a redirect URI for the given app scheme (e.g., "koogagent", "edgelab", "agentic") */
    fun getRedirectUri(appScheme: String) = "$appScheme://oauth/callback"
}
