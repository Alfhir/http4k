package org.http4k.security

import dev.forkhandles.result4k.Failure
import dev.forkhandles.result4k.Success
import dev.forkhandles.result4k.flatMap
import dev.forkhandles.result4k.get
import dev.forkhandles.result4k.map
import dev.forkhandles.result4k.mapFailure
import dev.forkhandles.result4k.peek
import org.http4k.core.HttpHandler
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status.Companion.TEMPORARY_REDIRECT
import org.http4k.security.oauth.server.AuthorizationCode
import org.http4k.security.openid.IdToken
import org.http4k.security.openid.IdTokenConsumer

class OAuthCallback(
    private val oAuthPersistence: OAuthPersistence,
    private val idTokenConsumer: IdTokenConsumer,
    private val accessTokenFetcher: AccessTokenFetcher
) : HttpHandler {

    override fun invoke(request: Request) = request.callbackParameters()
        .flatMap { parameters -> validateCsrf(parameters, request, oAuthPersistence.retrieveCsrf(request)) }
        .flatMap { parameters -> validateNonce(parameters, oAuthPersistence.retrieveNonce(request)) }
        .peek { parameters -> parameters.idToken?.let(idTokenConsumer::consumeFromAuthorizationResponse) }
        .flatMap { parameters -> fetchToken(parameters) }
        .peek { tokenDetails -> tokenDetails.idToken?.also(idTokenConsumer::consumeFromAccessTokenResponse) }
        .map { tokenDetails ->
            oAuthPersistence.assignToken(
                request,
                redirectionResponse(request),
                tokenDetails.accessToken,
                tokenDetails.idToken
            )
        }.mapFailure { oAuthPersistence.authFailureResponse() }.get()

    private fun Request.callbackParameters() = authorizationCode().map {
        CallbackParameters(
            code = it,
            state = queryOrFragmentParameter("state")?.let(::CrossSiteRequestForgeryToken),
            idToken = queryOrFragmentParameter("idToken")?.let(::IdToken)
        )
    }

    private fun Request.authorizationCode() = queryOrFragmentParameter("code")?.let(::AuthorizationCode)
        ?.let(::Success) ?: Failure(OauthCallbackError.AuthorizationCodeMissing)

    private fun validateCsrf(
        parameters: CallbackParameters,
        request: Request,
        persistedToken: CrossSiteRequestForgeryToken?
    ) = request.queryOrFragmentParameter("state")?.let(::CrossSiteRequestForgeryToken)
        ?.takeIf { it == persistedToken }
        ?.let { Success(parameters) } ?: Failure(OauthCallbackError.InvalidCsrfToken)

    private fun validateNonce(parameters: CallbackParameters, storedNonce: Nonce?) =
        parameters.idToken?.let { idToken ->
            if (idTokenConsumer.nonceFromIdToken(idToken) == storedNonce)
                Success(parameters) else Failure(OauthCallbackError.InvalidNonce)
        } ?: Success(parameters)

    private fun fetchToken(parameters: CallbackParameters) =
        accessTokenFetcher.fetch(parameters.code.value)?.let(::Success)
            ?: Failure(OauthCallbackError.CouldNotFetchAccessToken)

    private fun redirectionResponse(request: Request) = Response(TEMPORARY_REDIRECT)
        .header("Location", oAuthPersistence.retrieveOriginalUri(request)?.toString() ?: "/")

    private fun Request.queryOrFragmentParameter(name: String) = query(name) ?: fragmentParameter(name)

    private data class CallbackParameters(
        val code: AuthorizationCode,
        val state: CrossSiteRequestForgeryToken?,
        val idToken: IdToken?
    )
}

private sealed class OauthCallbackError {
    object AuthorizationCodeMissing : OauthCallbackError()
    object InvalidCsrfToken : OauthCallbackError()
    object InvalidNonce : OauthCallbackError()
    object CouldNotFetchAccessToken : OauthCallbackError()
}
