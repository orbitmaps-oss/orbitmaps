// SPDX-License-Identifier: GPL-3.0-or-later

package `in`.orbitmaps.app.regions

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import `in`.orbitmaps.app.net.OnlineData
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The Offline regions page and the list of regions installed on the phone. Downloads run while the
 * app is open (and resume later if it isn't); the region chosen is never sent anywhere except as the
 * name of the file that is fetched (PRIVACY.md).
 */
class RegionsViewModel(application: Application) : AndroidViewModel(application) {
    private val store = RegionStore(File(application.filesDir, "region-packs"))
    private val catalog = RegionCatalog(File(application.cacheDir, "regions"))

    /** Set by the activity from the network and the user's switch. */
    @Volatile var online: Boolean = false

    @Volatile var unmetered: Boolean = false

    @Volatile var wifiOnly: Boolean = true

    private val _installed = MutableStateFlow<List<InstalledRegion>>(emptyList())

    /** Regions on the phone, complete and verified; map, search and routing use these. */
    val installed: StateFlow<List<InstalledRegion>> = _installed.asStateFlow()

    private val entries = MutableStateFlow<List<CatalogEntry>?>(null)
    private val transient = MutableStateFlow<Map<String, RowState>>(emptyMap())
    private val loading = MutableStateFlow(false)
    private val jobs = mutableMapOf<String, Job>()

    val ui: StateFlow<RegionsUi> = MutableStateFlow(RegionsUi()).also { state ->
        viewModelScope.launch {
            combine(entries, _installed, transient, loading) { catalogue, installedNow, moving, busy ->
                RegionsUi(
                    rows = RegionRows.build(catalogue, installedNow, moving),
                    loading = busy,
                    catalogUnavailable = catalogue == null && !busy
                )
            }.collect { state.value = it }
        }
    }.asStateFlow()

    init {
        viewModelScope.launch { reloadInstalled() }
    }

    private suspend fun reloadInstalled() {
        _installed.value = withContext(Dispatchers.IO) { store.installed() }
    }

    /** Loads the catalogue: from the server when online, else the cached copy. */
    fun refresh() {
        if (loading.value) return
        viewModelScope.launch {
            loading.value = true
            val result = withContext(Dispatchers.IO) { catalog.load(allowNetwork = online) }
            entries.value = (result as? RegionCatalog.Result.Loaded)?.entries
            loading.value = false
        }
    }

    fun download(id: String) {
        val entry = entries.value?.firstOrNull { it.id == id } ?: return
        if (jobs[id]?.isActive == true) return
        RegionRows.blockedBy(online, unmetered, wifiOnly)?.let { problem ->
            setState(id, RowState.Failed(problem))
            return
        }
        jobs[id] = viewModelScope.launch {
            setState(id, RowState.Downloading(0, entry.sizeBytes))
            val loaded = withContext(Dispatchers.IO) { catalog.manifest(entry) }
            if (loaded == null) {
                setState(id, RowState.Failed(RegionProblem.Unavailable))
                return@launch
            }
            val (manifest, text) = loaded

            // Plain free space on purpose: we don't count on other apps' caches being cleared.
            @Suppress("UsableSpace")
            val freeBytes = getApplication<Application>().filesDir.usableSpace
            val result = withContext(Dispatchers.IO) {
                store.install(
                    manifest = manifest,
                    manifestText = text,
                    baseUrl = OnlineData.REGIONS_URL,
                    freeBytes = freeBytes,
                    shouldContinue = { isActive },
                    onProgress = { done, total -> setState(id, RowState.Downloading(done, total)) }
                )
            }
            when (result) {
                is PackInstall.Installed -> {
                    clearState(id)
                    reloadInstalled()
                }
                PackInstall.Cancelled -> clearState(id)
                is PackInstall.NoSpace -> setState(id, RowState.Failed(RegionProblem.NoSpace))
                PackInstall.Unavailable -> setState(id, RowState.Failed(RegionProblem.Unavailable))
                is PackInstall.Corrupt -> setState(id, RowState.Failed(RegionProblem.Corrupt))
            }
        }
    }

    /** Stops a download; what was downloaded is kept, so starting again resumes. */
    fun cancel(id: String) {
        jobs.remove(id)?.cancel()
        clearState(id)
    }

    fun delete(id: String) {
        jobs.remove(id)?.cancel()
        clearState(id)
        viewModelScope.launch {
            withContext(Dispatchers.IO) { store.delete(id) }
            reloadInstalled()
        }
    }

    private fun setState(id: String, state: RowState) {
        transient.value = transient.value + (id to state)
    }

    private fun clearState(id: String) {
        transient.value = transient.value - id
    }
}
