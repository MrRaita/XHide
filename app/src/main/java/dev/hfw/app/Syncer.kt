package dev.hfw.app

import dev.hfw.state.AppRule
import dev.hfw.state.Limits
import dev.hfw.state.Request
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Keeps system_server's replica equal to the [RuleStore]: replays the offline queue, then compares hashes and repairs
 * a difference (adopt on a never-synced empty store, otherwise a full Replace). A rejected edit is reverted locally.
 */
class Syncer(private val store: RuleStore, private val client: HookClient) {
    enum class Link { UNKNOWN, ONLINE, OFFLINE, REJECTED }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    private val _link = MutableStateFlow(Link.UNKNOWN)
    val link: StateFlow<Link> get() = _link

    private val _ping = MutableStateFlow<HookClient.Ping?>(null)
    val ping: StateFlow<HookClient.Ping?> get() = _ping

    /** The hook's ledger (overlay status per package), refreshed by [refreshOverlays]. */
    private val _hookApps = MutableStateFlow<Map<String, AppRule>>(emptyMap())
    val hookApps: StateFlow<Map<String, AppRule>> get() = _hookApps

    private val _hookUserApps = MutableStateFlow<Map<Int, Map<String, AppRule>>>(emptyMap())
    val hookUserApps: StateFlow<Map<Int, Map<String, AppRule>>> get() = _hookUserApps

    private val _overlayBlocked = MutableStateFlow(false)
    val overlayBlocked: StateFlow<Boolean> get() = _overlayBlocked
    private val _overlayLast = MutableStateFlow(0)
    /** 0 = no overlay attempt yet, 1 = the last attempt worked (module present), 2 = blocked by SELinux. */
    val overlayLast: StateFlow<Int> get() = _overlayLast

    /** True once an edit was refused by the hook and rolled back (the UI tells the user once). */
    private val _reverted = MutableStateFlow(0)
    val reverted: StateFlow<Int> get() = _reverted

    fun syncAsync() { scope.launch { sync() } }

    suspend fun sync() = mutex.withLock {
        val first = client.ping()
        if (first == null) { _link.value = Link.OFFLINE; return@withLock }
        _ping.value = first
        _link.value = Link.ONLINE

        // 1. replay the queue
        while (true) {
            val batch = store.batch(Limits.MAX_OPS, 80_000)
            if (batch.isEmpty()) break
            when (client.update(Request(UUID.randomUUID().toString(), batch))) {
                is HookClient.UpdateResult.Ok -> store.ack(batch)
                HookClient.UpdateResult.Unreachable -> { _link.value = Link.OFFLINE; return@withLock }
                HookClient.UpdateResult.Rejected -> {
                    val remote = client.stateGet()
                    if (remote == null) { _link.value = Link.REJECTED; return@withLock } // not authorized: keep the edits
                    store.adopt(remote) // refused edit: the hook's state wins, the local change is reverted
                    _reverted.value++
                    break
                }
            }
        }

        // 2. reconcile
        val now = client.ping() ?: run { _link.value = Link.OFFLINE; return@withLock }
        _ping.value = now
        if (store.hash() != now.hash) {
            if (!store.everSynced && store.isPristine()) {
                client.stateGet()?.let { store.adopt(it) }
            } else {
                when (client.update(Request(UUID.randomUUID().toString(), listOf(store.fullReplace())))) {
                    is HookClient.UpdateResult.Ok -> Unit
                    HookClient.UpdateResult.Unreachable -> { _link.value = Link.OFFLINE; return@withLock }
                    HookClient.UpdateResult.Rejected -> { _link.value = Link.REJECTED; return@withLock }
                }
            }
        }
        val fin = client.ping()
        if (fin != null && fin.hash == store.hash() && store.pendingCount == 0) store.markSynced()
        refreshOverlaysLocked()
    }

    private fun refreshOverlaysLocked() {
        client.stateGet()?.let {
            _hookApps.value = it.apps
            _hookUserApps.value = it.icons
        }
        client.status()?.let {
            _overlayBlocked.value = it.optBoolean("overlayBlocked", false)
            _overlayLast.value = it.optInt("overlayLast", 0)
        }
    }

    suspend fun refreshOverlays() = withContext(Dispatchers.IO) { mutex.withLock { refreshOverlaysLocked() } }
}
