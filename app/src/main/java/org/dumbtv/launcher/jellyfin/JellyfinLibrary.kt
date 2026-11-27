package org.dumbtv.launcher.jellyfin

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.SystemClock
import android.util.LruCache
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import javax.net.ssl.SSLException

class JellyfinLibrary(context: Context, private val scope: CoroutineScope) {
    data class Movie(val id: String, val title: String, val year: String, val imageTag: String?)
    data class State(val connected: Boolean = false, val loading: Boolean = false,
        val code: String? = null, val error: String? = null, val movies: List<Movie> = emptyList())
    private data class Session(val user: String, val token: String)
    private class HttpFailure(val status: Int) : Exception()
    private class NotJellyfin : Exception()

    val state = MutableStateFlow(State())
    private val store = SessionStore(context)
    private val serverPrefs = context.getSharedPreferences("jellyfin-server", Context.MODE_PRIVATE)
    /** Last server the user saved. Session tokens are bound to the origin stored with them. */
    val server = MutableStateFlow(readServer())
    private val deviceId = context.getSharedPreferences("jellyfin-device", Context.MODE_PRIVATE).let { prefs ->
        prefs.getString("id", null) ?: UUID.randomUUID().toString().also { prefs.edit().putString("id", it).apply() }
    }
    @Volatile private var session: Session? = null
    private var job: Job? = null
    private var lastRefresh = 0L
    private val posters = object : LruCache<String, Bitmap>(8 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    private val posterRequests = Semaphore(2)

    init {
        job = scope.launch {
            val restored = withContext(Dispatchers.IO) {
                val raw = store.read() ?: return@withContext null
                val parsed = runCatching {
                    val json = JSONObject(raw)
                    val auth = Session(
                        requireNotNull(JellyfinIds.uuid(json.getString("user"))),
                        json.getString("token").also { require(it.isNotBlank()) },
                    )
                    val target = (JellyfinServer.parse(json.getString("server"), "", false) as? JellyfinServer.Attempt.Valid)?.server
                    if (target == null) null else target to auth
                }.getOrNull()
                if (parsed == null) store.clear()
                parsed
            } ?: return@launch
            val (target, auth) = restored
            saveServer(target)
            session = auth
            state.value = State(connected = true)
            fetchMovies()
        }
    }

    fun refresh(force: Boolean = false) {
        if (session == null || job?.isActive == true) return
        if (!force && SystemClock.elapsedRealtime() - lastRefresh < 5 * 60_000) return
        job = scope.launch { fetchMovies() }
    }

    fun connect(address: String, portText: String, secure: Boolean) {
        if (job?.isActive == true) return
        val target = serverFrom(address, portText, secure) ?: return
        val previous = state.value
        job = scope.launch {
            state.value = previous.copy(loading = true, code = null, error = null)
            try {
                probe(target.origin)
                val enabled = jsonText(target.origin, "/QuickConnect/Enabled")
                if (!enabled.trim().equals("true", ignoreCase = true)) {
                    state.value = failureState(previous, "Enable Quick Connect in your Jellyfin server settings, then retry.")
                    return@launch
                }
                val started = try {
                    json(target.origin, "/QuickConnect/Initiate", "POST")
                } catch (failure: HttpFailure) {
                    if (failure.status == 405) json(target.origin, "/QuickConnect/Initiate") else throw failure
                }
                val secret = started.getString("Secret")
                state.value = state.value.copy(loading = true, code = started.getString("Code"), error = null)
                repeat(60) {
                    delay(3000)
                    val status = json(target.origin, "/QuickConnect/Connect?secret=" + java.net.URLEncoder.encode(secret, "UTF-8"))
                    if (status.optBoolean("Authenticated")) {
                        val result = json(target.origin, "/Users/AuthenticateWithQuickConnect", "POST", JSONObject().put("Secret", secret).toString())
                        acceptAuth(result, target)
                        return@launch
                    }
                }
                state.value = failureState(previous, "The code expired. Select Quick Connect to try again.")
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: HttpFailure) { state.value = failureState(previous, httpMessage(failure.status, signingIn = false)) }
            catch (_: SSLException) { state.value = failureState(previous, sslMessage(target.origin)) }
            catch (_: NotJellyfin) { state.value = failureState(previous, notJellyfin) }
            catch (_: Exception) { state.value = failureState(previous, offline(target.origin)) }
        }
    }

    fun signIn(address: String, portText: String, secure: Boolean, username: String, password: String) {
        if (job?.isActive == true) return
        val target = serverFrom(address, portText, secure) ?: return
        if (username.isBlank()) {
            state.value = failureState(state.value, "Enter your Jellyfin username.")
            return
        }
        val previous = state.value
        job = scope.launch {
            state.value = previous.copy(loading = true, code = null, error = null)
            try {
                probe(target.origin)
                val result = json(target.origin, "/Users/AuthenticateByName", "POST",
                    JSONObject().put("Username", username.trim()).put("Pw", password).toString())
                acceptAuth(result, target)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: HttpFailure) { state.value = failureState(previous, httpMessage(failure.status, signingIn = true)) }
            catch (_: SSLException) { state.value = failureState(previous, sslMessage(target.origin)) }
            catch (_: NotJellyfin) { state.value = failureState(previous, notJellyfin) }
            catch (_: Exception) { state.value = failureState(previous, offline(target.origin)) }
        }
    }

    private fun serverFrom(address: String, portText: String, secure: Boolean): JellyfinServer? {
        return when (val attempt = JellyfinServer.parse(address, portText, secure)) {
            is JellyfinServer.Attempt.Valid -> attempt.server
            is JellyfinServer.Attempt.Rejected -> {
                state.value = failureState(state.value, attempt.message)
                null
            }
        }
    }

    private suspend fun probe(base: String) {
        val text = try {
            jsonText(base, "/System/Info/Public")
        } catch (failure: HttpFailure) {
            if (failure.status == 404) throw NotJellyfin() else throw failure
        }
        val info = try {
            JSONObject(text)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            throw NotJellyfin()
        }
        if (info.optString("Version").isBlank() && info.optString("ServerName").isBlank() && info.optString("Id").isBlank()) {
            throw NotJellyfin()
        }
    }

    private suspend fun acceptAuth(result: JSONObject, target: JellyfinServer) {
        val auth = Session(
            requireNotNull(JellyfinIds.uuid(result.getJSONObject("User").getString("Id"))),
            result.getString("AccessToken").also { require(it.isNotBlank()) },
        )
        if (server.value?.origin != target.origin) posters.evictAll()
        saveServer(target)
        withContext(Dispatchers.IO) {
            store.write(JSONObject().put("user", auth.user).put("token", auth.token).put("server", target.origin).toString())
        }
        session = auth
        state.value = State(connected = true)
        fetchMovies()
    }

    fun cancelConnect() {
        if (state.value.code == null && !state.value.loading) return
        val previous = job
        previous?.cancel()
        state.value = if (session != null) state.value.copy(connected = true, loading = false, code = null, error = null) else State()
        job = scope.launch { previous?.join() }
    }

    fun disconnect() {
        val previous = job
        previous?.cancel()
        session = null
        lastRefresh = 0
        posters.evictAll()
        state.value = State()
        job = scope.launch { previous?.join(); withContext(Dispatchers.IO) { store.clear() } }
    }

    private suspend fun fetchMovies() {
        val auth = session ?: return
        val base = server.value?.origin ?: run {
            state.value = State(error = "Add your Jellyfin server address.")
            return
        }
        state.value = state.value.copy(connected = true, loading = true, code = null, error = null)
        try {
            val response = json(base, "/Users/${auth.user}/Items?Recursive=true&IncludeItemTypes=Movie&SortBy=DateCreated&SortOrder=Descending&Limit=24&EnableImages=true&ImageTypeLimit=1", token = auth.token)
            val items = response.getJSONArray("Items")
            val movies = buildList {
                for (index in 0 until items.length()) {
                    val item = items.getJSONObject(index)
                    val id = JellyfinIds.uuid(item.optString("Id")) ?: continue
                    add(Movie(id, item.optString("Name", "Untitled"), item.optInt("ProductionYear").takeIf { it > 0 }?.toString().orEmpty(),
                        item.optJSONObject("ImageTags")?.optString("Primary")?.takeIf { it.matches(Regex("[a-zA-Z0-9-]+")) }))
                }
            }
            if (session != auth || server.value?.origin != base) return
            lastRefresh = SystemClock.elapsedRealtime()
            state.value = State(connected = true, movies = movies)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: HttpFailure) {
            if (failure.status == 401 || failure.status == 403) {
                session = null
                withContext(Dispatchers.IO) { store.clear() }
                posters.evictAll()
                state.value = State(error = "Jellyfin access expired or was denied. Connect again with the correct profile.")
            } else state.value = state.value.copy(loading = false, error = "Jellyfin could not load movies (${failure.status}).")
        } catch (_: Exception) {
            state.value = state.value.copy(loading = false, error = "Jellyfin is offline. Showing the last loaded movies.")
        }
    }

    suspend fun poster(movie: Movie): Bitmap? {
        val auth = session ?: return null
        val base = server.value?.origin ?: return null
        val tag = movie.imageTag ?: return null
        val key = "$base:${auth.user}:${movie.id}:$tag"
        posters.get(key)?.let { return it }
        return posterRequests.withPermit {
            posters.get(key)?.let { return@withPermit it }
            try {
                withContext(Dispatchers.IO) {
                    val bytes = request(base, "/Items/${movie.id}/Images/Primary?maxWidth=240&quality=80&tag=$tag", token = auth.token, maxBytes = 2 * 1024 * 1024)
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                    if (bounds.outWidth !in 1..2048 || bounds.outHeight !in 1..2048) return@withContext null
                    val image = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply {
                        inPreferredConfig = Bitmap.Config.RGB_565
                        var sample = 1
                        while (bounds.outWidth / sample > 240 || bounds.outHeight / sample > 480) sample *= 2
                        inSampleSize = sample
                    }) ?: return@withContext null
                    image.prepareToDraw()
                    if (session != auth || server.value?.origin != base) return@withContext null
                    posters.put(key, image)
                    image
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { null }
        }
    }

    private fun readServer(): JellyfinServer? {
        val host = serverPrefs.getString("host", null) ?: return null
        val port = serverPrefs.getInt("port", 8096)
        val secure = serverPrefs.getBoolean("secure", false)
        return (JellyfinServer.parse(host, port.toString(), secure) as? JellyfinServer.Attempt.Valid)?.server
    }

    private fun saveServer(target: JellyfinServer) {
        server.value = target
        serverPrefs.edit().putString("host", target.host).putInt("port", target.port).putBoolean("secure", target.secure).apply()
    }

    private fun failureState(previous: State, message: String): State =
        if (session != null) previous.copy(loading = false, code = null, error = message) else State(error = message)

    private fun httpMessage(status: Int, signingIn: Boolean) = when {
        status in 300..399 -> "The server sent a redirect. Enter its final http or https address."
        status == 401 && signingIn -> "Jellyfin did not accept that username or password."
        status == 401 -> "Quick Connect is unavailable. Enable it in the Jellyfin server settings."
        status == 404 -> notJellyfin
        signingIn -> "Jellyfin rejected sign-in ($status)."
        else -> "Jellyfin rejected the request ($status)."
    }

    private fun sslMessage(origin: String) = "HTTPS to $origin failed. A local server is usually HTTP on port 8096."
    private fun offline(origin: String) = "Cannot reach $origin. Check the IP, port, and that the TV is on the same network."

    private suspend fun json(base: String, path: String, method: String = "GET", body: String? = null, token: String? = null) =
        JSONObject(jsonText(base, path, method, body, token))
    private suspend fun jsonText(base: String, path: String, method: String = "GET", body: String? = null, token: String? = null) =
        withContext(Dispatchers.IO) { String(request(base, path, method, body, token), Charsets.UTF_8) }

    private fun request(base: String, path: String, method: String = "GET", body: String? = null, token: String? = null, maxBytes: Int = 2 * 1024 * 1024): ByteArray {
        require(path.startsWith("/") && !path.startsWith("//"))
        require(base.startsWith("http://") || base.startsWith("https://"))
        val connection = URL(base + path).openConnection() as HttpURLConnection
        return try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            connection.requestMethod = method
            connection.setRequestProperty("Authorization", "MediaBrowser Client=\"Dumb TV\", Device=\"Sony TV\", DeviceId=\"$deviceId\", Version=\"0.2.0\"")
            if (token != null) connection.setRequestProperty("X-Emby-Token", token)
            if (method == "POST") {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write((body ?: "{}").toByteArray(Charsets.UTF_8)) }
            }
            if (connection.responseCode !in 200..299) throw HttpFailure(connection.responseCode)
            if (connection.contentLengthLong > maxBytes) error("Response too large")
            connection.inputStream.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    check(output.size() + count <= maxBytes) { "Response too large" }
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
        } finally { connection.disconnect() }
    }

    private companion object {
        const val notJellyfin = "That address did not respond as a Jellyfin server."
    }
}
