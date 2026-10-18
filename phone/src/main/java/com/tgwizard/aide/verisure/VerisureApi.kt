package com.tgwizard.aide.verisure

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Minimal client for Verisure's (unofficial) app API, ported from the `vsure` Python library
 * that Home Assistant uses. Cookies are the session; callers persist [cookies] between runs.
 */
class VerisureApi(var cookies: Map<String, String> = emptyMap()) {

    open class VerisureException(message: String) : Exception(message)
    class AuthException(message: String) : VerisureException(message)
    class RateLimitException(message: String) : VerisureException(message)

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private var baseUrls = listOf("https://automation01.verisure.com", "https://automation02.verisure.com")

    /** Returns true if Verisure asks for a second factor (call [requestMfa] then [validateMfa]). */
    suspend fun login(email: String, password: String): Boolean {
        // A trust cookie from an earlier MFA lets us skip the second factor.
        val body = call("/auth/login", EMPTY, authorization = Credentials.basic(email, password), only = { it.startsWith("vs-trust") })
        return "stepUpToken" in body
    }

    suspend fun requestMfa() {
        try {
            call("/auth/mfa?type=phone", EMPTY)
        } catch (e: VerisureException) {
            if (e is RateLimitException) throw e
            call("/auth/mfa?type=email", EMPTY)
        }
    }

    suspend fun validateMfa(code: String) {
        call("/auth/mfa/validate", JSONObject().put("token", code).toString().toRequestBody(JSON))
        call("/auth/trust", EMPTY)
    }

    /** Session cookies last ~15 min; this exchanges the refresh cookie for new ones. */
    suspend fun refresh() {
        call("/auth/token", body = null, only = { it == "vid" || it == "vs-refresh" })
    }

    /** Runs one GraphQL operation and returns its `data` object. */
    suspend fun graphql(operationName: String, query: String, variables: JSONObject): JSONObject {
        val payload = JSONArray().put(
            JSONObject().put("operationName", operationName).put("variables", variables).put("query", query),
        )
        val text = call("/graphql", payload.toString().toRequestBody(JSON))
        // The API answers a one-element batch with either an object or a one-element array.
        val result = text.trim().let { if (it.startsWith("[")) JSONArray(it).getJSONObject(0) else JSONObject(it) }
        result.optJSONArray("errors")?.takeIf { it.length() > 0 }?.let { errors ->
            val message = errors.toString()
            if ("AUT_" in message || "UNAUTHENTICATED" in message.uppercase()) throw AuthException(message)
            throw VerisureException(message)
        }
        return result.optJSONObject("data") ?: throw VerisureException("No data in response")
    }

    /**
     * POSTs [body] (or GETs, if null) to [path] on each base URL in turn, like vsure does, sending
     * the cookies whose names match [only] and storing any cookies the response sets.
     */
    private suspend fun call(
        path: String,
        body: RequestBody?,
        authorization: String? = null,
        only: (String) -> Boolean = { true },
    ): String =
        withContext(Dispatchers.IO) {
            val cookieHeader = cookies.filterKeys(only).entries.joinToString("; ") { "${it.key}=${it.value}" }
            var lastError: Exception = VerisureException("No Verisure server reachable")
            for (base in baseUrls) {
                val built = Request.Builder()
                    .url(base + path)
                    .apply { if (body != null) post(body) }
                    .header("APPLICATION_ID", "PS_PYTHON")
                    .header("Accept", "application/json")
                    .apply { if (authorization != null) header("Authorization", authorization) }
                    .apply { if (cookieHeader.isNotEmpty()) header("Cookie", cookieHeader) }
                    .build()
                try {
                    http.newCall(built).execute().use { response ->
                        val text = response.body.string()
                        val setCookies = response.headers("Set-Cookie").mapNotNull { header ->
                            header.substringBefore(';').split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0].trim() to it[1].trim() }
                        }
                        if (setCookies.isNotEmpty()) cookies = cookies + setCookies
                        when {
                            response.code == 429 || isRateLimited(text) -> throw RateLimitException(text)
                            response.code == 401 || response.code == 403 -> throw AuthException(text)
                            response.code >= 500 || "SYS_00004" in text -> {
                                lastError = VerisureException("Server error ${response.code}")
                                baseUrls = baseUrls.reversed()
                            }
                            response.code >= 400 -> throw VerisureException("HTTP ${response.code}: $text")
                            else -> return@withContext text
                        }
                    }
                } catch (e: IOException) {
                    Log.w(TAG, "Request to $base$path failed", e)
                    lastError = e
                    baseUrls = baseUrls.reversed()
                }
            }
            throw lastError
        }

    private fun isRateLimited(text: String): Boolean = text.lowercase().let { lower ->
        listOf("aut_00021", "acc_00002", "toomanystepuptokens", "request limit", "too many requests").any { it in lower }
    }

    companion object {
        private const val TAG = "AideVerisure"
        private val JSON = "application/json".toMediaType()
        private val EMPTY = ByteArray(0).toRequestBody(null)
    }
}
