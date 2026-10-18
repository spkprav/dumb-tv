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

class JellyfinLibrary(context: Context, private val scope: CoroutineScope) {
    data class Movie(val id: String, val title: String, val year: String, val imageTag: String?)
    data class State(val connected: Boolean = false, val loading: Boolean = false,
        val code: String? = null, val error: String? = null, val movies: List<Movie> = emptyList())
    private data class Session(val user: String, val token: String)
    private class HttpFailure(val status: Int) : Exception()
    val state = MutableStateFlow(State())
    private val store = SessionStore(context)
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
                store.read()?.let { raw -> runCatching {
                    val json = JSONObject(raw)
                    Session(requireNotNull(JellyfinIds.uuid(json.getString("user"))), json.getString("token").also { require(it.isNotBlank()) })
                }.getOrNull() }
            }
            session = restored
            if (restored != null) {
                state.value = State(connected = true)
                fetchMovies()
            }
        }
    }

    fun refresh(force: Boolean = false) {
        if (session == null || job?.isActive == true) return
        if (!force && SystemClock.elapsedRealtime() - lastRefresh < 5 * 60_000) return
        job = scope.launch { fetchMovies() }
    }

    fun connect() {
        if (job?.isActive == true) return
        job = scope.launch {
            state.value = State(loading = true)
            try {
                val enabled = jsonText("/QuickConnect/Enabled")
                if (enabled.trim() != "true") {
                    state.value = State(error = "Enable Quick Connect in your Jellyfin server settings, then retry.")
                    return@launch
                }
                val started = try { json("/QuickConnect/Initiate", "POST") }
                    catch (failure: HttpFailure) { if (failure.status == 405) json("/QuickConnect/Initiate") else throw failure }
                val secret = started.getString("Secret")
                state.value = State(loading = true, code = started.getString("Code"))
                repeat(60) {
                    delay(3000)
                    val status = json("/QuickConnect/Connect?secret=" + java.net.URLEncoder.encode(secret, "UTF-8"))
                    if (status.optBoolean("Authenticated")) {
                        val result = json("/Users/AuthenticateWithQuickConnect", "POST", JSONObject().put("Secret", secret).toString())
                        acceptAuth(result)
                        return@launch
                    }
                }
                state.value = State(error = "The code expired. Select Connect to try again.")
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: HttpFailure) {
                state.value = State(error = if (failure.status == 401) "Quick Connect is unavailable. Enable it on your server." else "Jellyfin rejected sign-in (${failure.status}). Try a new code.")
            } catch (_: Exception) {
                state.value = State(error = "Cannot connect to $SERVER. Check that Jellyfin is running and the TV is on the same network.")
            }
        }
    }

    fun signIn(username: String, password: String) {
        if (job?.isActive == true) return
        if (username.isBlank()) { state.value = State(error = "Enter your Jellyfin username."); return }
        job = scope.launch {
            state.value = State(loading = true)
            try {
                val result = json("/Users/AuthenticateByName", "POST", JSONObject().put("Username", username).put("Pw", password).toString())
                acceptAuth(result)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: HttpFailure) {
                state.value = State(error = if (failure.status == 401) "Jellyfin did not accept that username/password." else "Jellyfin rejected sign-in (${failure.status}).")
            } catch (_: Exception) { state.value = State(error = "Cannot sign in to $SERVER. Check the connection and try again.") }
        }
    }

    private suspend fun acceptAuth(result: JSONObject) {
        val auth = Session(requireNotNull(JellyfinIds.uuid(result.getJSONObject("User").getString("Id"))),
            result.getString("AccessToken").also { require(it.isNotBlank()) })
        withContext(Dispatchers.IO) { store.write(JSONObject().put("user", auth.user).put("token", auth.token).toString()) }
        session = auth
        state.value = State(connected = true)
        fetchMovies()
    }

    fun cancelConnect() {
        if (session == null && state.value.loading) {
            val previous = job
            previous?.cancel()
            state.value = State()
            job = scope.launch { previous?.join(); withContext(Dispatchers.IO) { store.clear() } }
        }
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
        state.value = state.value.copy(connected = true, loading = true, code = null, error = null)
        try {
            val response = json("/Users/${auth.user}/Items?Recursive=true&IncludeItemTypes=Movie&SortBy=DateCreated&SortOrder=Descending&Limit=24&EnableImages=true&ImageTypeLimit=1", token = auth.token)
            val items = response.getJSONArray("Items")
            val movies = buildList {
                for (index in 0 until items.length()) {
                    val item = items.getJSONObject(index)
                    val id = JellyfinIds.uuid(item.optString("Id")) ?: continue
                    add(Movie(id, item.optString("Name", "Untitled"), item.optInt("ProductionYear").takeIf { it > 0 }?.toString().orEmpty(),
                        item.optJSONObject("ImageTags")?.optString("Primary")?.takeIf { it.matches(Regex("[a-zA-Z0-9-]+")) }))
                }
            }
            if (session != auth) return
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
        val tag = movie.imageTag ?: return null
        val key = "${auth.user}:${movie.id}:$tag"
        posters.get(key)?.let { return it }
        return posterRequests.withPermit {
            posters.get(key)?.let { return@withPermit it }
            try {
                withContext(Dispatchers.IO) {
                    val bytes = request("/Items/${movie.id}/Images/Primary?maxWidth=240&quality=80&tag=$tag", token = auth.token, maxBytes = 2 * 1024 * 1024)
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
                    if (session != auth) return@withContext null
                    posters.put(key, image)
                    image
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { null }
        }
    }

    private suspend fun json(path: String, method: String = "GET", body: String? = null, token: String? = null) =
        JSONObject(jsonText(path, method, body, token))
    private suspend fun jsonText(path: String, method: String = "GET", body: String? = null, token: String? = null) =
        withContext(Dispatchers.IO) { String(request(path, method, body, token), Charsets.UTF_8) }

    private fun request(path: String, method: String = "GET", body: String? = null, token: String? = null, maxBytes: Int = 2 * 1024 * 1024): ByteArray {
        require(path.startsWith("/") && !path.startsWith("//"))
        val connection = URL(SERVER + path).openConnection() as HttpURLConnection
        return try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 4000
            connection.readTimeout = 4000
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

    companion object { const val SERVER = "http://jellyfin.example.invalid:8096" }
}
