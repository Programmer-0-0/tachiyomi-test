package eu.kanade.presentation.updates.failed

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.res.stringResource
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.coroutineScope
import eu.kanade.core.util.addOrRemove
import eu.kanade.domain.manga.interactor.UpdateManga
import eu.kanade.domain.source.interactor.GetSourcesWithFavoriteCount
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.library.LibraryUpdateJob
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.removeCovers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import logcat.LogPriority
import tachiyomi.core.preference.PreferenceStore
import tachiyomi.core.preference.getEnum
import tachiyomi.core.util.lang.launchIO
import tachiyomi.core.util.lang.launchNonCancellable
import tachiyomi.core.util.system.logcat
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.library.model.LibraryManga
import tachiyomi.domain.manga.interactor.GetLibraryManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaUpdate
import tachiyomi.domain.source.model.Source
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.TreeMap

class FailedUpdatesScreenModel(
    private val getLibraryManga: GetLibraryManga = Injekt.get(),
    private val sourceManager: SourceManager = Injekt.get(),
    private val downloadManager: DownloadManager = Injekt.get(),
    private val updateManga: UpdateManga = Injekt.get(),
    private val coverCache: CoverCache = Injekt.get(),
    private val getCategories: GetCategories = Injekt.get(),
    private val getSourcesWithFavoriteCount: GetSourcesWithFavoriteCount = Injekt.get(),
    private val preferenceStore: PreferenceStore = Injekt.get(),
) : StateScreenModel<FailedUpdatesScreenState>(FailedUpdatesScreenState()) {

    var failedUpdates = LibraryUpdateJob.getNewFailedUpdates()
    private val selectedPositions: Array<Int> = arrayOf(-1, -1)
    private val selectedMangaIds: HashSet<Long> = HashSet()
    private val _channel = Channel<Event>(Int.MAX_VALUE)
    val channel = _channel.receiveAsFlow()

    init {
        coroutineScope.launchIO {
            val sortMode = preferenceStore.getEnum("sort_mode", SortingMode.BY_ALPHABET).get()
            getSourcesWithFavoriteCount.subscribe()
                .catch {
                    logcat(LogPriority.ERROR, it)
                    _channel.send(Event.FailedFetchingSourcesWithCount)
                }
                .collectLatest { sources ->
                    mutableState.update { state ->
                        val categories = getCategories.await().associateBy { group -> group.id }
                        state.copy(
                            sourcesCount = sources,
                            items = getLibraryManga.await().filter { libraryManga ->
                                failedUpdates.any { it.first.id == libraryManga.manga.id }
                            }.map { libraryManga ->
                                val source = sourceManager.get(libraryManga.manga.source)!!
                                val errorMessage = failedUpdates.find {
                                    it.first.id == libraryManga.manga.id
                                }?.second
                                FailedUpdatesManga(
                                    libraryManga = libraryManga,
                                    errorMessage = errorMessage,
                                    selected = libraryManga.id in selectedMangaIds,
                                    source = source,
                                    category = categories[libraryManga.category]!!,
                                )
                            },
                            groupByMode = preferenceStore.getEnum("group_by_mode", GroupByMode.NONE).get(),
                            sortMode = sortMode,
                            descendingOrder = preferenceStore.getBoolean("descending_order", false).get(),
                            isLoading = false,
                        )
                    }
                }
            runSortAction(sortMode)
        }
    }

    fun runSortAction(mode: SortingMode) {
        when (mode) {
            SortingMode.BY_ALPHABET -> sortByAlphabet()
        }
    }

    fun runGroupBy(mode: GroupByMode) {
        when (mode) {
            GroupByMode.NONE -> unGroup()
            GroupByMode.BY_CATEGORY -> groupByCategory()
            GroupByMode.BY_SOURCE -> groupBySource()
        }
    }

    private fun sortByAlphabet() {
        mutableState.update { state ->
            val descendingOrder = if (state.sortMode == SortingMode.BY_ALPHABET) !state.descendingOrder else false
            preferenceStore.getBoolean("descending_order", false).set(descendingOrder)
            state.copy(
                items = if (descendingOrder) state.items.sortedByDescending { it.libraryManga.manga.title } else state.items.sortedBy { it.libraryManga.manga.title },
                descendingOrder = descendingOrder,
                sortMode = SortingMode.BY_ALPHABET,
            )
        }
        preferenceStore.getEnum("sort_mode", SortingMode.BY_ALPHABET).set(SortingMode.BY_ALPHABET)
    }

    @Composable
    fun categoryMap(items: List<FailedUpdatesManga>, groupMode: GroupByMode, sortMode: SortingMode, descendingOrder: Boolean): Map<String, List<FailedUpdatesManga>> {
        val unsortedMap = when (groupMode) {
            GroupByMode.BY_CATEGORY -> items.groupBy { if (it.category.isSystemCategory) { stringResource(R.string.label_default) } else { it.category.name } }
            GroupByMode.BY_SOURCE -> items.groupBy { it.source.name }
            GroupByMode.NONE -> emptyMap()
        }
        return when (sortMode) {
            SortingMode.BY_ALPHABET -> {
                val sortedMap = TreeMap<String, List<FailedUpdatesManga>>(if (descendingOrder) { compareByDescending { it } } else { compareBy { it } })
                sortedMap.putAll(unsortedMap)
                sortedMap
            }
        }
    }

    private fun groupBySource() {
        mutableState.update {
            it.copy(
                groupByMode = GroupByMode.BY_SOURCE,
            )
        }
        preferenceStore.getEnum("group_by_mode", GroupByMode.NONE).set(GroupByMode.BY_SOURCE)
    }

    private fun groupByCategory() {
        mutableState.update {
            it.copy(
                groupByMode = GroupByMode.BY_CATEGORY,
            )
        }
        preferenceStore.getEnum("group_by_mode", GroupByMode.NONE).set(GroupByMode.BY_CATEGORY)
    }

    private fun unGroup() {
        mutableState.update {
            it.copy(
                groupByMode = GroupByMode.NONE,
            )
        }
        preferenceStore.getEnum("group_by_mode", GroupByMode.NONE).set(GroupByMode.NONE)
    }

    fun toggleSelection(
        item: FailedUpdatesManga,
        selected: Boolean,
        userSelected: Boolean = false,
        fromLongPress: Boolean = false,
    ) {
        mutableState.update { state ->
            val newItems = state.items.toMutableList().apply {
                val selectedIndex = indexOfFirst { it.libraryManga.manga.id == item.libraryManga.manga.id }
                if (selectedIndex < 0) return@apply

                val selectedItem = get(selectedIndex)
                if (selectedItem.selected == selected) return@apply

                val firstSelection = none { it.selected }
                set(selectedIndex, selectedItem.copy(selected = selected))
                selectedMangaIds.addOrRemove(item.libraryManga.manga.id, selected)

                if (selected && userSelected && fromLongPress) {
                    if (firstSelection) {
                        selectedPositions[0] = selectedIndex
                        selectedPositions[1] = selectedIndex
                    } else {
                        // Try to select the items in-between when possible
                        val range: IntRange
                        if (selectedIndex < selectedPositions[0]) {
                            range = selectedIndex + 1 until selectedPositions[0]
                            selectedPositions[0] = selectedIndex
                        } else if (selectedIndex > selectedPositions[1]) {
                            range = (selectedPositions[1] + 1) until selectedIndex
                            selectedPositions[1] = selectedIndex
                        } else {
                            // Just select itself
                            range = IntRange.EMPTY
                        }

                        range.forEach {
                            val inBetweenItem = get(it)
                            if (!inBetweenItem.selected) {
                                selectedMangaIds.add(inBetweenItem.libraryManga.manga.id)
                                set(it, inBetweenItem.copy(selected = true))
                            }
                        }
                    }
                } else if (userSelected && !fromLongPress) {
                    if (!selected) {
                        if (selectedIndex == selectedPositions[0]) {
                            selectedPositions[0] = indexOfFirst { it.selected }
                        } else if (selectedIndex == selectedPositions[1]) {
                            selectedPositions[1] = indexOfLast { it.selected }
                        }
                    } else {
                        if (selectedIndex < selectedPositions[0]) {
                            selectedPositions[0] = selectedIndex
                        } else if (selectedIndex > selectedPositions[1]) {
                            selectedPositions[1] = selectedIndex
                        }
                    }
                }
            }
            state.copy(items = newItems)
        }
    }

    fun toggleAllSelection(selected: Boolean) {
        mutableState.update { state ->
            val newItems = state.items.map {
                selectedMangaIds.addOrRemove(it.libraryManga.manga.id, selected)
                it.copy(selected = selected)
            }
            state.copy(items = newItems)
        }

        selectedPositions[0] = -1
        selectedPositions[1] = -1
    }

    fun removeMangas(mangaList: List<Manga>, deleteFromLibrary: Boolean, deleteChapters: Boolean) {
        coroutineScope.launchNonCancellable {
            val mangaToDelete = mangaList.distinctBy { it.id }

            if (deleteFromLibrary) {
                val toDelete = mangaToDelete.map {
                    it.removeCovers(coverCache)
                    MangaUpdate(
                        favorite = false,
                        id = it.id,
                    )
                }
                updateManga.awaitAll(toDelete)
            }

            if (deleteChapters) {
                mangaToDelete.forEach { manga ->
                    val source = sourceManager.get(manga.source) as? HttpSource
                    if (source != null) {
                        downloadManager.deleteManga(manga, source)
                    }
                }
            }
        }
        if (deleteFromLibrary) {
            val set = mangaList.map { it.id }.toHashSet()
            mutableState.update { state ->
                state.copy(
                    items = state.items.filterNot { it.libraryManga.id in set },
                )
            }
            failedUpdates = failedUpdates.filterNot { it.first.id in set }
            LibraryUpdateJob.setNewFailedUpdates(set)
        }
    }

    fun dismissManga(selected: List<FailedUpdatesManga>) {
        val set = selected.map { it.libraryManga.id }.toHashSet()
        toggleAllSelection(false)
        mutableState.update { state ->
            state.copy(
                items = state.items.filterNot { it.libraryManga.id in set },
            )
        }
        failedUpdates = failedUpdates.filterNot { it.first.id in set }
        LibraryUpdateJob.setNewFailedUpdates(set)
    }

    fun openDeleteMangaDialog(selected: List<FailedUpdatesManga>) {
        val mangaList = selected.map { it.libraryManga.manga }
        mutableState.update { it.copy(dialog = Dialog.DeleteManga(mangaList)) }
    }

    fun closeDialog() {
        mutableState.update { it.copy(dialog = null) }
    }

    fun invertSelection() {
        mutableState.update { state ->
            val newItems = state.items.map {
                selectedMangaIds.addOrRemove(it.libraryManga.manga.id, !it.selected)
                it.copy(selected = !it.selected)
            }
            state.copy(items = newItems)
        }
        selectedPositions[0] = -1
        selectedPositions[1] = -1
    }

    fun groupSelection(items: List<FailedUpdatesManga>) {
        val newSelected = items.map { manga -> manga.libraryManga.id }.toHashSet()
        selectedMangaIds.addAll(newSelected)
        mutableState.update { state ->
            val newItems = state.items.map {
                it.copy(selected = if (it.libraryManga.id in newSelected) !it.selected else it.selected)
            }
            state.copy(items = newItems)
        }
        selectedPositions[0] = -1
        selectedPositions[1] = -1
    }
}

enum class GroupByMode {
    NONE,
    BY_CATEGORY,
    BY_SOURCE,
}

enum class SortingMode {
    BY_ALPHABET,
}

sealed class Dialog {
    data class DeleteManga(val manga: List<Manga>) : Dialog()
}

sealed class Event {
    data object FailedFetchingSourcesWithCount : Event()
}

@Immutable
data class FailedUpdatesManga(
    val libraryManga: LibraryManga,
    val errorMessage: String?,
    val selected: Boolean = false,
    val source: eu.kanade.tachiyomi.source.Source,
    val category: Category,
)

@Immutable
data class FailedUpdatesScreenState(
    val isLoading: Boolean = true,
    val items: List<FailedUpdatesManga> = emptyList(),
    val groupByMode: GroupByMode = GroupByMode.NONE,
    val sortMode: SortingMode = SortingMode.BY_ALPHABET,
    val descendingOrder: Boolean = false,
    val dialog: Dialog? = null,
    val sourcesCount: List<Pair<Source, Long>> = emptyList(),
) {
    val selected = items.filter { it.selected }
    val selectionMode = selected.isNotEmpty()
}
