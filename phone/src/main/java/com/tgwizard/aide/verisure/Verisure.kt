package com.tgwizard.aide.verisure

import android.content.Context
import android.util.Log
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

/**
 * Verisure: account setup (used by the phone UI), the door lock and sensors (used by the watch).
 * All API traffic is serialized and results are cached, so the watch can ask freely without
 * hammering Verisure.
 */
object Verisure {

    data class Lock(val deviceLabel: String, val name: String)

    data class State(
        val lock: String?,
        val door: String?,
        val changedBy: String?,
        val changedAt: Long?,
        val method: String?,
        val fetchedAt: Long,
    )

    class LoginNeededException : VerisureApi.VerisureException("Verisure login needed")
    class NotConfiguredException : VerisureApi.VerisureException("Verisure not set up")

    private val mutex = Mutex()
    private var api: VerisureApi? = null
    private var lastRefreshAt = 0L
    private var cached: State? = null
    private var cachedHome: Pair<Long, JSONObject>? = null

    /** Credentials between [login] and [verifyMfa]; never persisted until login succeeds. */
    private var pending: Pair<String, String>? = null

    // --- Setup (phone UI) ---

    fun account(context: Context): SecureStore.Account? = SecureStore(context).load()

    /** Returns true if an SMS/email code was sent and [verifyMfa] must be called next. */
    suspend fun login(context: Context, email: String, password: String): Boolean = mutex.withLock {
        val api = VerisureApi(SecureStore(context).load()?.takeIf { it.email == email }?.cookies.orEmpty())
        this.api = api
        if (api.login(email, password)) {
            pending = email to password
            api.requestMfa()
            return true
        }
        SecureStore(context).save(SecureStore.Account(email, password, api.cookies))
        lastRefreshAt = System.currentTimeMillis()
        false
    }

    suspend fun verifyMfa(context: Context, code: String) = mutex.withLock {
        val (email, password) = pending ?: throw VerisureApi.VerisureException("No login in progress")
        val api = api ?: throw VerisureApi.VerisureException("No login in progress")
        api.validateMfa(code.trim())
        // Log in again now that we hold a trust cookie, to get regular session cookies.
        if (api.login(email, password)) throw VerisureApi.VerisureException("Verisure still asks for a code")
        SecureStore(context).save(SecureStore.Account(email, password, api.cookies))
        lastRefreshAt = System.currentTimeMillis()
        pending = null
    }

    /** Picks the first installation and returns its locks; selects the lock if there's only one. */
    suspend fun discoverLocks(context: Context): List<Lock> = mutex.withLock {
        withSession(context) { api, account ->
            val installations = api.graphql("fetchAllInstallations", Queries.INSTALLATIONS, JSONObject().put("email", account.email))
                .getJSONObject("account").getJSONArray("installations")
            if (installations.length() == 0) throw VerisureApi.VerisureException("No installations on this account")
            val installation = installations.getJSONObject(0)
            val giid = installation.getString("giid")
            val withGiid = account.copy(giid = giid, installationName = installation.optString("alias").ifEmpty { null })
            SecureStore(context).save(withGiid)

            val locks = smartLocks(api, giid).map { Lock(it.getJSONObject("device").getString("deviceLabel"), lockName(it)) }
            locks.singleOrNull()?.let { SecureStore(context).save(withGiid.copy(deviceLabel = it.deviceLabel, lockName = it.name)) }
            locks
        }
    }

    fun selectLock(context: Context, lock: Lock) = update(context) { it.copy(deviceLabel = lock.deviceLabel, lockName = lock.name) }

    fun setDoorCode(context: Context, code: String) = update(context) { it.copy(doorCode = code.trim()) }

    fun logout(context: Context) {
        SecureStore(context).clear()
        api = null
        cached = null
        cachedHome = null
        pending = null
    }

    private fun update(context: Context, change: (SecureStore.Account) -> SecureStore.Account) {
        val store = SecureStore(context)
        store.load()?.let { store.save(change(it)) }
        cached = null
    }

    // --- Watch-facing ---

    /** Door status as JSON for the watch (see wear/.../DoorStatus.kt); never throws. */
    suspend fun statusJson(context: Context, maxAgeMs: Long = 60_000): JSONObject = report(context) {
        cached?.takeIf { System.currentTimeMillis() - it.fetchedAt < maxAgeMs } ?: fetch(context)
    }

    suspend fun lockJson(context: Context): JSONObject = report(context) { change(context, LOCKED) }

    suspend fun unlockJson(context: Context): JSONObject = report(context) { change(context, UNLOCKED) }

    /** Door/window sensors and climate readings as JSON for the watch (see wear/.../HomeStatus.kt); never throws. */
    suspend fun homeJson(context: Context, maxAgeMs: Long = 60_000): JSONObject = try {
        mutex.withLock {
            val fresh = cachedHome?.takeIf { System.currentTimeMillis() - it.first < maxAgeMs }?.second
            JSONObject((fresh ?: fetchHome(context).also { cachedHome = System.currentTimeMillis() to it }).toString())
        }
    } catch (e: Exception) {
        Log.w(TAG, "Home request failed", e)
        // Still show the last known state, flagged with the error.
        JSONObject(cachedHome?.second?.toString() ?: "{}").put("error", errorCode(e))
    }

    private suspend fun report(context: Context, block: suspend () -> State): JSONObject {
        val account = account(context)
        val json = JSONObject().put("configured", account?.deviceLabel != null && account.doorCode != null)
            .put("name", account?.lockName)
        return try {
            mutex.withLock { block() }.let { state ->
                json.put("lock", state.lock).put("door", state.door).put("by", state.changedBy)
                    .put("at", state.changedAt).put("method", state.method)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Door request failed", e)
            json.put("error", errorCode(e))
                // Still show the last known state, flagged with the error.
                .apply { cached?.let { put("lock", it.lock).put("door", it.door).put("by", it.changedBy).put("at", it.changedAt) } }
        }
    }

    private fun errorCode(e: Exception): String = when (e) {
        is NotConfiguredException -> "not_configured"
        is LoginNeededException, is VerisureApi.AuthException -> "login_needed"
        is VerisureApi.RateLimitException -> "rate_limited"
        is VerisureApi.VerisureException -> "verisure"
        else -> "network"
    }

    private suspend fun fetch(context: Context): State = withSession(context) { api, account ->
        val giid = account.giid ?: throw NotConfiguredException()
        val label = account.deviceLabel ?: throw NotConfiguredException()
        val lock = smartLocks(api, giid).firstOrNull { it.getJSONObject("device").getString("deviceLabel") == label }
            ?: throw VerisureApi.VerisureException("Lock $label not found")
        State(
            lock = lock.optString("lockStatus").ifEmpty { null },
            door = lock.optString("doorState").ifEmpty { null },
            changedBy = lock.optJSONObject("user")?.optString("name")?.ifEmpty { null },
            changedAt = parseTime(lock.optString("eventTime")),
            method = lock.optString("lockMethod").ifEmpty { null },
            fetchedAt = System.currentTimeMillis(),
        ).also { cached = it }
    }

    private suspend fun change(context: Context, target: String): State {
        val confirmed = withSession(context) { api, account ->
            val giid = account.giid ?: throw NotConfiguredException()
            val label = account.deviceLabel ?: throw NotConfiguredException()
            val code = account.doorCode ?: throw NotConfiguredException()
            val operation = if (target == LOCKED) "DoorLock" else "DoorUnlock"
            val variables = JSONObject().put("giid", giid).put("deviceLabel", label).put("input", JSONObject().put("code", code))
            val transactionId = api.graphql(operation, if (target == LOCKED) Queries.LOCK else Queries.UNLOCK, variables).optString(operation)
            Log.i(TAG, "$operation sent, transaction $transactionId")

            // Same polling as Home Assistant: up to ~15 s until the lock confirms.
            val pollVariables = JSONObject().put("giid", giid).put("transactionId", transactionId)
                .put("deviceLabel", label).put("futureState", target)
            (0 until 30).any { attempt ->
                if (attempt > 1) delay(500)
                api.graphql("pollLockState", Queries.POLL_LOCK_STATE, pollVariables)
                    .getJSONObject("installation").optJSONObject("doorLockStateChangePollResult")
                    ?.optString("result") == "OK"
            }
        }
        val state = fetch(context)
        // The status endpoint can lag the confirmed change by a few seconds.
        return if (confirmed && state.lock != target) state.copy(lock = target).also { cached = it } else state
    }

    private suspend fun fetchHome(context: Context): JSONObject = withSession(context) { api, account ->
        val variables = JSONObject().put("giid", account.giid ?: throw NotConfiguredException())
        val openings = api.graphql("DoorWindow", Queries.DOOR_WINDOW, variables)
            .getJSONObject("installation").getJSONArray("doorWindows").objects()
            .map { sensor ->
                JSONObject()
                    .put("name", sensor.optString("area").ifEmpty { sensor.getJSONObject("device").optString("deviceLabel") })
                    .put("type", sensor.optString("type"))
                    .put("open", sensor.optString("state") == "OPEN")
                    .put("at", parseTime(sensor.optString("reportTime")))
            }
        val climates = api.graphql("Climate", Queries.CLIMATE, variables)
            .getJSONObject("installation").getJSONArray("climates").objects()
            .filter { it.has("temperatureValue") && !it.isNull("temperatureValue") }
            .map { sensor ->
                JSONObject()
                    .put("name", sensor.getJSONObject("device").optString("area"))
                    .put("temp", sensor.getDouble("temperatureValue"))
                    .apply {
                        if (sensor.optBoolean("humidityEnabled") && !sensor.isNull("humidityValue")) {
                            put("humidity", sensor.getDouble("humidityValue"))
                        }
                    }
            }
        JSONObject().put("openings", JSONArray(openings)).put("climates", JSONArray(climates))
    }

    private suspend fun smartLocks(api: VerisureApi, giid: String) =
        api.graphql("SmartLock", Queries.SMART_LOCK, JSONObject().put("giid", giid))
            .getJSONObject("installation").getJSONArray("smartLocks").objects()

    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }

    private fun parseTime(iso: String): Long? =
        iso.ifEmpty { null }?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }

    private fun lockName(lock: JSONObject) =
        lock.getJSONObject("device").optString("area").ifEmpty { lock.getJSONObject("device").getString("deviceLabel") }

    /**
     * Runs [block] with a live session: refreshes cookies every 10 min, and on an auth error
     * refreshes (or logs in again with the stored password and trust cookie) and retries once.
     */
    private suspend fun <T> withSession(context: Context, block: suspend (VerisureApi, SecureStore.Account) -> T): T {
        val store = SecureStore(context)
        val account = store.load() ?: throw NotConfiguredException()
        val session = api ?: VerisureApi(account.cookies).also { api = it }

        suspend fun renew() {
            try {
                session.refresh()
            } catch (e: VerisureApi.RateLimitException) {
                throw e
            } catch (e: Exception) {
                Log.i(TAG, "Token refresh failed, logging in again", e)
                if (session.login(account.email, account.password)) throw LoginNeededException()
            }
            lastRefreshAt = System.currentTimeMillis()
            store.save(store.load()!!.copy(cookies = session.cookies))
        }

        if (System.currentTimeMillis() - lastRefreshAt > REFRESH_INTERVAL_MS) renew()
        return try {
            block(session, store.load()!!)
        } catch (e: VerisureApi.AuthException) {
            Log.i(TAG, "Auth error, renewing session", e)
            renew()
            block(session, store.load()!!)
        }
    }

    private const val TAG = "AideDoor"
    private const val LOCKED = "LOCKED"
    private const val UNLOCKED = "UNLOCKED"
    private const val REFRESH_INTERVAL_MS = 10 * 60_000L
}

/** GraphQL documents, verbatim from vsure 2.10.1. */
private object Queries {
    const val INSTALLATIONS = "query fetchAllInstallations(\$email: String!){\n  account(email: \$email) {\n    installations {\n      giid\n      alias\n      customerType\n      dealerId\n      subsidiary\n      pinCodeLength\n      locale\n      address {\n        street\n        city\n        postalNumber\n        __typename\n      }\n      __typename\n    }\n    __typename\n  }\n}\n"
    const val SMART_LOCK = "query SmartLock(\$giid: String!) {\n  installation(giid: \$giid) {\n    smartLocks {\n      lockStatus\n      doorState\n      lockMethod\n      eventTime\n      doorLockType\n      secureMode\n      device {\n        deviceLabel\n        area\n        __typename\n      }\n      user {\n        name\n        __typename\n      }\n      __typename\n    }\n    __typename\n  }\n}\n"
    const val LOCK = "mutation DoorLock(\$giid: String!, \$deviceLabel: String!, \$input: LockDoorInput!) {\n  DoorLock(giid: \$giid, deviceLabel: \$deviceLabel, input: \$input)\n}\n"
    const val UNLOCK = "mutation DoorUnlock(\$giid: String!, \$deviceLabel: String!, \$input: LockDoorInput!) {\n  DoorUnlock(giid: \$giid, deviceLabel: \$deviceLabel, input: \$input)\n}\n"
    const val DOOR_WINDOW = "query DoorWindow(\$giid: String!) {\n  installation(giid: \$giid) {\n    doorWindows {\n      device {\n        deviceLabel\n        __typename\n      }\n      type\n      area\n      state\n      wired\n      reportTime\n      __typename\n    }\n    __typename\n  }\n}\n"
    const val CLIMATE = "query Climate(\$giid: String!) {\n  installation(giid: \$giid) {\n    climates {\n      device {\n        deviceLabel\n        area\n        gui {\n          label\n          __typename\n        }\n        __typename\n      }\n      humidityEnabled\n      humidityTimestamp\n      humidityValue\n      temperatureTimestamp\n      temperatureValue\n      thresholds {\n        aboveMaxAlert\n        belowMinAlert\n        sensorType\n        __typename\n      }\n      __typename\n    }\n    __typename\n  }\n}\n"
    const val POLL_LOCK_STATE = "query pollLockState(\$giid: String!, \$transactionId: String, \$deviceLabel: String!, \$futureState: DoorLockState!) {\n  installation(giid: \$giid) {\n    doorLockStateChangePollResult(transactionId: \$transactionId, deviceLabel: \$deviceLabel, futureState: \$futureState) {\n      result\n      createTime\n      __typename\n    }\n    __typename\n  }\n}\n"
}
