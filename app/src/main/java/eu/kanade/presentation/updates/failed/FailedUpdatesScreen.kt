package eu.kanade.presentation.updates.failed

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowLeft
import androidx.compose.material.icons.outlined.ArrowRight
import androidx.compose.material.icons.outlined.FlipToBack
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.Sort
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.presentation.components.DropdownMenu
import eu.kanade.presentation.components.NestedMenuItem
import eu.kanade.presentation.library.DeleteLibraryMangaDialog
import eu.kanade.presentation.manga.components.FailedUpdatesBottomActionMenu
import eu.kanade.presentation.updates.setWarningIconEnabled
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.browse.migration.search.MigrateSearchScreen
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import tachiyomi.domain.manga.model.Manga
import tachiyomi.presentation.core.components.FastScrollLazyColumn
import tachiyomi.presentation.core.components.Pill
import tachiyomi.presentation.core.components.SortItem
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen
import tachiyomi.source.local.isLocal

class FailedUpdatesScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow

        val screenModel = rememberScreenModel { FailedUpdatesScreenModel() }
        val state by screenModel.state.collectAsState()

        val categoryExpandedMapSaver: Saver<MutableMap<String, Boolean>, *> = Saver(
            save = { map -> map.toMap() },
            restore = { map -> mutableStateMapOf(*map.toList().toTypedArray()) },
        )
        var expanded = emptyMap<String, Boolean>().toMutableMap()

        if (screenModel.failedUpdates.isEmpty()) {
            setWarningIconEnabled(false)
        }

        Scaffold(
            topBar = { scrollBehavior ->
                FailedUpdatesAppBar(
                    groupByMode = state.groupByMode,
                    items = state.items,
                    selected = state.selected,
                    onSelectAll = { screenModel.toggleAllSelection(true) },
                    onIgnoreAll = { screenModel.dismissManga(state.items) },
                    onExpandAll = { expanded.keys.forEach { expanded[it] = true } },
                    onContractAll = { expanded.keys.forEach { expanded[it] = false } },
                    onInvertSelection = { screenModel.invertSelection() },
                    onCancelActionMode = { screenModel.toggleAllSelection(false) },
                    scrollBehavior = scrollBehavior,
                    onClickGroup = screenModel::runGroupBy,
                    onClickSort = screenModel::runSortAction,
                    sortState = state.sortMode,
                    descendingOrder = state.descendingOrder,
                    navigateUp = navigator::pop,
                    errorCount = state.items.size,
                )
            },
            bottomBar = {
                FailedUpdatesBottomActionMenu(
                    visible = state.selectionMode,
                    onDeleteClicked = { screenModel.openDeleteMangaDialog(state.selected) },
                    onDismissClicked = { screenModel.dismissManga(state.selected) },
                )
            },
        ) { contentPadding ->
            when {
                state.isLoading -> LoadingScreen(modifier = Modifier.padding(contentPadding))

                state.items.isEmpty() -> EmptyScreen(
                    textResource = R.string.information_no_update_errors,
                    modifier = Modifier.padding(contentPadding),
                    happyFace = true,
                )

                else -> {
                    when (state.groupByMode) {
                        GroupByMode.NONE -> FastScrollLazyColumn(
                            contentPadding = contentPadding,
                        ) {
                            failedUpdatesUiItems(
                                items = state.items,
                                selectionMode = state.selectionMode,
                                onClick = { item ->
                                    navigator.push(
                                        MangaScreen(item.libraryManga.manga.id),
                                    )
                                },
                                onSelected = screenModel::toggleSelection,
                                onMigrateManga = { item ->
                                    navigator.push(
                                        MigrateSearchScreen(item.libraryManga.manga.id),
                                    )
                                },
                            )
                        }

                        GroupByMode.BY_SOURCE -> {
                            val categoryMap = screenModel.categoryMap(
                                state.items,
                                GroupByMode.BY_SOURCE,
                                state.sortMode,
                                state.descendingOrder,
                            )
                            expanded = rememberSaveable(
                                saver = categoryExpandedMapSaver,
                                key = "CategoryExpandedMap",
                                init = { mutableStateMapOf(*categoryMap.keys.toList().map { it to false }.toTypedArray()) },
                            )
                            CategoryList(
                                contentPadding = contentPadding,
                                selectionMode = state.selectionMode,
                                onMangaClick = { item ->
                                    navigator.push(
                                        MangaScreen(item.libraryManga.manga.id),
                                    )
                                },
                                onMigrateManga = { item ->
                                    navigator.push(
                                        MigrateSearchScreen(item.libraryManga.manga.id),
                                    )
                                },
                                onGroupSelected = screenModel::groupSelection,
                                onSelected = screenModel::toggleSelection,
                                categoryMap = categoryMap,
                                isSourceOrCategory = 1,
                                expanded = expanded,
                                sourcesCount = state.sourcesCount,
                            )
                        }

                        GroupByMode.BY_CATEGORY -> {
                            val categoryMap = screenModel.categoryMap(
                                state.items,
                                GroupByMode.BY_CATEGORY,
                                state.sortMode,
                                state.descendingOrder,
                            )
                            expanded = rememberSaveable(
                                saver = categoryExpandedMapSaver,
                                key = "CategoryExpandedMap",
                                init = { mutableStateMapOf(*categoryMap.keys.toList().map { it to false }.toTypedArray()) },
                            )
                            CategoryList(
                                contentPadding = contentPadding,
                                selectionMode = state.selectionMode,
                                onMangaClick = { item ->
                                    navigator.push(
                                        MangaScreen(item.libraryManga.manga.id),
                                    )
                                },
                                onMigrateManga = { item ->
                                    navigator.push(
                                        MigrateSearchScreen(item.libraryManga.manga.id),
                                    )
                                },
                                onGroupSelected = screenModel::groupSelection,
                                onSelected = screenModel::toggleSelection,
                                categoryMap = categoryMap,
                                isSourceOrCategory = 2,
                                expanded = expanded,
                                sourcesCount = state.sourcesCount,
                            )
                        }
                    }
                }
            }
        }
        val onDismissRequest = screenModel::closeDialog
        when (val dialog = state.dialog) {
            is Dialog.DeleteManga -> {
                DeleteLibraryMangaDialog(
                    containsLocalManga = dialog.manga.any(Manga::isLocal),
                    onDismissRequest = onDismissRequest,
                    onConfirm = { deleteManga, deleteChapter ->
                        screenModel.removeMangas(dialog.manga, deleteManga, deleteChapter)
                        screenModel.toggleAllSelection(false)
                    },
                )
            }
            null -> {}
        }
    }
}

@Composable
private fun FailedUpdatesAppBar(
    groupByMode: GroupByMode,
    items: List<FailedUpdatesManga>,
    modifier: Modifier = Modifier,
    selected: List<FailedUpdatesManga>,
    onSelectAll: () -> Unit,
    onInvertSelection: () -> Unit,
    onCancelActionMode: () -> Unit,
    onClickSort: (SortingMode) -> Unit,
    onClickGroup: (GroupByMode) -> Unit,
    onIgnoreAll: () -> Unit,
    onExpandAll: () -> Unit,
    onContractAll: () -> Unit,
    scrollBehavior: TopAppBarScrollBehavior,
    sortState: SortingMode,
    descendingOrder: Boolean? = null,
    navigateUp: (() -> Unit)?,
    errorCount: Int,
) {
    if (selected.isNotEmpty()) {
        FailedUpdatesActionAppBar(
            modifier = modifier,
            onSelectAll = onSelectAll,
            onInvertSelection = onInvertSelection,
            onCancelActionMode = onCancelActionMode,
            scrollBehavior = scrollBehavior,
            navigateUp = navigateUp,
            actionModeCounter = selected.size,
        )
        BackHandler(
            onBack = onCancelActionMode,
        )
    } else {
        AppBar(
            navigateUp = navigateUp,
            titleContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.label_failed_updates),
                        maxLines = 1,
                        modifier = Modifier.weight(1f, false),
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (errorCount > 0) {
                        val pillAlpha = if (isSystemInDarkTheme()) 0.12f else 0.08f
                        Pill(
                            text = "$errorCount",
                            modifier = Modifier.padding(start = 4.dp),
                            color = MaterialTheme.colorScheme.onBackground
                                .copy(alpha = pillAlpha),
                            fontSize = 14.sp,
                        )
                    }
                }
            },
            actions = {
                if (items.isNotEmpty()) {
                    val filterTint = LocalContentColor.current
                    var sortExpanded by remember { mutableStateOf(false) }
                    val onSortDismissRequest = { sortExpanded = false }
                    var mainExpanded by remember { mutableStateOf(false) }
                    var expandAll by remember { mutableStateOf(false) }
                    val onDismissRequest = { mainExpanded = false }
                    SortDropdownMenu(
                        expanded = sortExpanded,
                        onDismissRequest = onSortDismissRequest,
                        onSortClicked = onClickSort,
                        sortState = sortState,
                        descendingOrder = descendingOrder,
                    )
                    DropdownMenu(expanded = mainExpanded, onDismissRequest = onDismissRequest) {
                        NestedMenuItem(
                            text = { Text(text = stringResource(R.string.action_groupBy)) },
                            children = { closeMenu ->
                                DropdownMenuItem(
                                    text = { Text(text = stringResource(R.string.action_group_by_source)) },
                                    onClick = {
                                        onClickGroup(GroupByMode.BY_SOURCE)
                                        closeMenu()
                                        onDismissRequest()
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(text = stringResource(R.string.action_group_by_category)) },
                                    onClick = {
                                        onClickGroup(GroupByMode.BY_CATEGORY)
                                        closeMenu()
                                        onDismissRequest()
                                    },
                                )
                            },
                        )
                        val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
                        DropdownMenuItem(
                            text = { Text(text = stringResource(R.string.action_sortBy)) },
                            onClick = {
                                onDismissRequest()
                                sortExpanded = !sortExpanded
                            },
                            trailingIcon = {
                                Icon(
                                    imageVector = if (isLtr) Icons.Outlined.ArrowRight else Icons.Outlined.ArrowLeft,
                                    contentDescription = null,
                                )
                            },
                        )
                    }
                    val actions = mutableListOf<AppBar.AppBarAction>()
                    actions += AppBar.Action(
                        title = stringResource(R.string.action_sort),
                        icon = Icons.Outlined.Sort,
                        iconTint = filterTint,
                        onClick = { mainExpanded = !mainExpanded },
                    )
                    actions += AppBar.OverflowAction(
                        title = stringResource(R.string.action_ignore_all),
                        onClick = onIgnoreAll,
                    )
                    if (groupByMode != GroupByMode.NONE) {
                        if (expandAll) {
                            actions += AppBar.OverflowAction(
                                title = stringResource(R.string.action_contract_all),
                                onClick = {
                                    onContractAll()
                                    expandAll = false
                                },
                            )
                        } else {
                            actions += AppBar.OverflowAction(
                                title = stringResource(R.string.action_expand_all),
                                onClick = {
                                    onExpandAll()
                                    expandAll = true
                                },
                            )
                        }
                    }
                    if (groupByMode != GroupByMode.NONE) {
                        actions += AppBar.OverflowAction(
                            title = stringResource(R.string.action_ungroup),
                            onClick = { onClickGroup(GroupByMode.NONE) },
                        )
                    }
                    AppBarActions(actions)
                }
            },
            scrollBehavior = scrollBehavior,
        )
    }
}

@Composable
private fun FailedUpdatesActionAppBar(
    modifier: Modifier = Modifier,
    actionModeCounter: Int,
    onSelectAll: () -> Unit,
    onInvertSelection: () -> Unit,
    onCancelActionMode: () -> Unit,
    scrollBehavior: TopAppBarScrollBehavior,
    navigateUp: (() -> Unit)?,
) {
    AppBar(
        modifier = modifier,
        title = stringResource(R.string.label_failed_updates),
        actionModeCounter = actionModeCounter,
        onCancelActionMode = onCancelActionMode,
        actionModeActions = {
            AppBarActions(
                listOf(
                    AppBar.Action(
                        title = stringResource(R.string.action_select_all),
                        icon = Icons.Outlined.SelectAll,
                        onClick = onSelectAll,
                    ),
                    AppBar.Action(
                        title = stringResource(R.string.action_select_inverse),
                        icon = Icons.Outlined.FlipToBack,
                        onClick = onInvertSelection,
                    ),
                ),
            )
        },
        scrollBehavior = scrollBehavior,
        navigateUp = navigateUp,
    )
}

@Composable
fun SortDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    onSortClicked: (SortingMode) -> Unit,
    sortState: SortingMode,
    descendingOrder: Boolean? = null,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
    ) {
        SortItem(
            label = stringResource(R.string.action_sort_A_Z),
            sortDescending = descendingOrder.takeIf { sortState == SortingMode.BY_ALPHABET },
            onClick = { onSortClicked(SortingMode.BY_ALPHABET) },
        )
    }
}
