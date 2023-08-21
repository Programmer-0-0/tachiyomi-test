package eu.kanade.presentation.updates.failed

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowLeft
import androidx.compose.material.icons.outlined.ArrowRight
import androidx.compose.material.icons.outlined.FlipToBack
import androidx.compose.material.icons.outlined.HelpOutline
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalUriHandler
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
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import eu.kanade.tachiyomi.util.system.copyToClipboard
import tachiyomi.domain.manga.model.Manga
import tachiyomi.presentation.core.components.FastScrollLazyColumn
import tachiyomi.presentation.core.components.Pill
import tachiyomi.presentation.core.components.SortItem
import tachiyomi.presentation.core.components.material.ExtendedFloatingActionButton
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen
import tachiyomi.presentation.core.util.isScrolledToEnd
import tachiyomi.presentation.core.util.isScrollingUp
import tachiyomi.source.local.isLocal

class FailedUpdatesScreen : Screen() {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val uriHandler = LocalUriHandler.current

        val screenModel = rememberScreenModel { FailedUpdatesScreenModel(context) }
        val state by screenModel.state.collectAsState()
        val failedUpdatesListState = rememberLazyListState()

        val categoryExpandedMapSaver: Saver<MutableMap<GroupKey, Boolean>, *> = Saver(
            save = { map -> map.mapKeys { (key, _) -> key.categoryOrSource to key.errorMessagePair } },
            restore = { map ->
                val temp = mutableListOf<Pair<GroupKey, Boolean>>()
                for ((key, value) in map) {
                    temp.add(GroupKey(key.first, key.second) to value)
                }
                mutableStateMapOf(*temp.toTypedArray())
            },
        )

        var expanded = emptyMap<GroupKey, Boolean>().toMutableMap()

        Scaffold(
            topBar = { scrollBehavior ->
                FailedUpdatesAppBar(
                    groupByMode = state.groupByMode,
                    items = state.items,
                    selected = state.selected,
                    onSelectAll = { screenModel.toggleAllSelection(true) },
                    onDismissAll = { screenModel.dismissManga(state.items) },
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
                    onInfoClicked = { errorMessage ->
                        screenModel.openErrorMessageDialog(errorMessage)
                    },
                    selected = state.selected,
                    groupingMode = state.groupByMode,
                )
            },
            floatingActionButton = {
                AnimatedVisibility(
                    visible = state.items.isNotEmpty(),
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    ExtendedFloatingActionButton(
                        text = { Text(text = stringResource(R.string.label_help)) },
                        icon = { Icon(imageVector = Icons.Outlined.HelpOutline, contentDescription = null) },
                        onClick = { uriHandler.openUri("https://tachiyomi.org/help/guides/troubleshooting") },
                        expanded = failedUpdatesListState.isScrollingUp() || failedUpdatesListState.isScrolledToEnd(),
                    )
                }
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
                            state = failedUpdatesListState,
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
                                groupingMode = state.groupByMode,
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
                                init = {
                                    mutableStateMapOf(
                                        *categoryMap.keys.flatMap { source ->
                                            listOf(GroupKey(source, Pair("", "")) to false)
                                        }.toTypedArray(),
                                        *categoryMap.flatMap { entry ->
                                            entry.value.keys.map { errorMessage ->
                                                GroupKey(entry.key, errorMessage) to false
                                            }
                                        }.toTypedArray(),
                                    )
                                },
                            )
                            CategoryList(
                                contentPadding = contentPadding,
                                selectionMode = state.selectionMode,
                                onMangaClick = { item ->
                                    navigator.push(
                                        MangaScreen(item.libraryManga.manga.id),
                                    )
                                },
                                onGroupSelected = screenModel::groupSelection,
                                onSelected = { item, selected, userSelected, fromLongPress ->
                                    screenModel.toggleSelection(item, selected, userSelected, fromLongPress, true)
                                },
                                categoryMap = categoryMap,
                                expanded = expanded,
                                sourcesCount = state.sourcesCount,
                                onClickIcon = { errorMessage ->
                                    screenModel.openErrorMessageDialog(errorMessage)
                                },
                                onLongClickIcon = { errorMessage ->
                                    context.copyToClipboard(errorMessage, errorMessage)
                                },
                                lazyListState = failedUpdatesListState,
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
            is Dialog.ShowErrorMessage -> {
                ErrorMessageDialog(
                    onDismissRequest = onDismissRequest,
                    onCopyClick = {
                        context.copyToClipboard(dialog.errorMessage, dialog.errorMessage)
                        screenModel.toggleAllSelection(false)
                    },
                    errorMessage = dialog.errorMessage,
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
    onDismissAll: () -> Unit,
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
                        title = stringResource(R.string.action_dismiss_all),
                        onClick = onDismissAll,
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
