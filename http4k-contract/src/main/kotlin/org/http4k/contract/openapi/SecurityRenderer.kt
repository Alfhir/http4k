package org.http4k.contract.openapi

import org.http4k.contract.security.Security
import org.http4k.format.Json

typealias Render<NODE> = Json<NODE>.() -> NODE

/**
 * Provides rendering of Security models in to OpenApi specs.
 */
interface SecurityRenderer {
    fun <NODE> full(security: Security): Render<NODE>?
    fun <NODE> ref(security: Security): Render<NODE>?

    companion object {
        operator fun invoke(vararg renderers: SecurityRenderer): SecurityRenderer = object : SecurityRenderer {
            override fun <NODE> full(security: Security) =
                renderers.asSequence().mapNotNull { it.full<NODE>(security) }.firstOrNull()

            override fun <NODE> ref(security: Security): Render<NODE>? =
                renderers.asSequence().mapNotNull { it.ref<NODE>(security) }.firstOrNull()
        }
    }
}