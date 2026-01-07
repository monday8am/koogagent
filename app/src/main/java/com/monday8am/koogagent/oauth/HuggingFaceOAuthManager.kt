package com.monday8am.koogagent.oauth

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.ResponseTypeValues
import co.touchlab.kermit.Logger
import net.openid.appauth.CodeVerifierUtil
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Manages HuggingFace OAuth authentication flow using AppAuth library.
 */
class HuggingFaceOAuthManager(
    context: Context,
    private val clientId: String,
) {
    private val serviceConfig = AuthorizationServiceConfiguration(
        HuggingFaceOAuthConfig.AUTHORIZATION_ENDPOINT.toUri(),
        HuggingFaceOAuthConfig.TOKEN_ENDPOINT.toUri()
    )

    private val authService = AuthorizationService(context)

    /**
     * Creates an authorization intent for launching the OAuth flow in Custom Chrome Tab.
     * Uses PKCE (S256) for enhanced security.
     */
    fun createAuthorizationIntent(): Intent {
        val authRequest = AuthorizationRequest.Builder(
            serviceConfig,
            clientId,
            ResponseTypeValues.CODE,
            HuggingFaceOAuthConfig.REDIRECT_URI.toUri()
        )
            .setScope(HuggingFaceOAuthConfig.SCOPE)
            .setCodeVerifier(CodeVerifierUtil.generateRandomCodeVerifier())
            .build()

        Logger.d { "Creating authorization intent for client: $clientId" }
        return authService.getAuthorizationRequestIntent(authRequest)
    }

    /**
     * Handles the authorization response from the OAuth callback.
     * Extracts the authorization response and exchanges the code for an access token.
     *
     * @param intent The intent received from the OAuth redirect
     * @return The access token on success
     * @throws AuthorizationException if authorization fails
     * @throws IllegalStateException if the response is invalid
     */
    suspend fun handleAuthorizationResponse(intent: Intent): String {
        val response = AuthorizationResponse.fromIntent(intent)
        val exception = AuthorizationException.fromIntent(intent)

        return when {
            exception != null -> {
                throw exception
            }

            response != null -> {
                exchangeCodeForToken(response)
            }

            else -> {
                throw IllegalStateException("Unknown authorization result")
            }
        }
    }

    private suspend fun exchangeCodeForToken(response: AuthorizationResponse): String =
        suspendCancellableCoroutine { continuation ->
            Logger.d { "Exchanging code for token" }
            val tokenRequest = response.createTokenExchangeRequest()

            authService.performTokenRequest(tokenRequest) { tokenResponse, exception ->
                when {
                    tokenResponse?.accessToken != null -> {
                        Logger.d { "Access token received successfully" }
                        continuation.resume(tokenResponse.accessToken!!)
                    }

                    exception != null -> {
                        Logger.e(exception) { "Token exchange failed: ${exception.errorDescription}" }
                        continuation.resumeWithException(exception)
                    }

                    else -> {
                        val error = IllegalStateException("No token received in response")
                        Logger.e(error) { "Token exchange failed: empty response" }
                        continuation.resumeWithException(error)
                    }
                }
            }
        }

    /**
     * Releases resources held by the authorization service.
     * Call this when the manager is no longer needed.
     */
    fun dispose() {
        authService.dispose()
    }
}
