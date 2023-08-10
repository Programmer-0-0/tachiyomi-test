package eu.kanade.presentation.updates.failed

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.FlightTakeoff
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastAny
import androidx.core.graphics.drawable.toBitmap
import eu.kanade.presentation.manga.components.MangaCover
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.util.system.LocaleHelper
import tachiyomi.domain.source.model.Source
import tachiyomi.presentation.core.components.FastScrollLazyColumn
import tachiyomi.presentation.core.components.Pill
import tachiyomi.presentation.core.components.material.TextButton
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.util.secondaryItemAlpha
import tachiyomi.presentation.core.util.selectedBackground
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

fun LazyListScope.failedUpdatesUiItems(
    items: List<FailedUpdatesManga>,
    selectionMode: Boolean,
    onSelected: (FailedUpdatesManga, Boolean, Boolean, Boolean) -> Unit,
    onClick: (FailedUpdatesManga) -> Unit,
    onMigrateManga: (FailedUpdatesManga) -> Unit,
    isUngrouped: Boolean,
) {
    items(
        items = items,
        key = { it.libraryManga.manga.id },
    ) { item ->
        Box(modifier = Modifier.animateItemPlacement(animationSpec = tween(300))) {
            FailedUpdatesUiItem(
                modifier = Modifier,
                selected = item.selected,
                onLongClick = {
                    onSelected(item, !item.selected, true, true)
                },
                onClick = {
                    when {
                        selectionMode -> onSelected(item, !item.selected, true, false)
                        else -> onClick(item)
                    }
                },
                manga = item,
                onMigrateManga = { onMigrateManga(item) }.takeIf { !selectionMode },
                isUngrouped = isUngrouped,
            )
        }
    }
}

@Composable
private fun FailedUpdatesUiItem(
    modifier: Modifier,
    manga: FailedUpdatesManga,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMigrateManga: (() -> Unit)?,
    isUngrouped: Boolean = false,
    padding: Dp = MaterialTheme.padding.medium,
) {
    val haptic = LocalHapticFeedback.current
    val textAlpha = 1f
    Row(
        modifier = modifier
            .selectedBackground(selected)
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    onLongClick()
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                },
            )
            .height(56.dp)
            .padding(start = padding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MangaCover.Square(
            modifier = Modifier
                .padding(vertical = 6.dp)
                .fillMaxHeight(),
            data = manga.libraryManga.manga,
        )

        Column(
            modifier = Modifier
                .padding(start = MaterialTheme.padding.medium)
                .weight(1f)
                .animateContentSize(),
        ) {
            Text(
                text = manga.libraryManga.manga.title,
                maxLines = 1,
                style = MaterialTheme.typography.bodyMedium,
                color = LocalContentColor.current.copy(alpha = textAlpha),
                overflow = TextOverflow.Ellipsis,
            )
            if (isUngrouped) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    var textHeight by remember { mutableIntStateOf(0) }
                    Text(
                        text = manga.errorMessage ?: "Null",
                        maxLines = if (selected) Int.MAX_VALUE else 1,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        onTextLayout = { textHeight = it.size.height },
                        modifier = Modifier
                            .weight(weight = 1f, fill = false),
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        MigrateIndicator(
            modifier = Modifier.padding(start = 4.dp),
            onClick = { onMigrateManga?.invoke() },
        )
    }
}

fun returnSourceIcon(id: Long): ImageBitmap? {
    return Injekt.get<ExtensionManager>().getAppIconForSource(id)
        ?.toBitmap()
        ?.asImageBitmap()
}

fun LazyListScope.failedUpdatesGroupUiItem(
    errorMessageMap: Map<String?, List<FailedUpdatesManga>>,
    selectionMode: Boolean,
    onSelected: (FailedUpdatesManga, Boolean, Boolean, Boolean) -> Unit,
    onMangaClick: (FailedUpdatesManga) -> Unit,
    id: String,
    onMigrateManga: (FailedUpdatesManga) -> Unit,
    onGroupSelected: (List<FailedUpdatesManga>) -> Unit,
    expanded: MutableMap<Pair<String, String?>, Boolean>,
    showLanguageInContent: Boolean = true,
    isSourceOrCategory: Int,
    sourcesCount: List<Pair<Source, Long>>,
) {
    var key = ""
    errorMessageMap.values.flatten().forEachIndexed { index, item ->
        key = "${item.category.id}_$index"
    }
    stickyHeader(
        key = if (isSourceOrCategory == 1) {
            errorMessageMap.values.flatten().find { it.source.name == id }!!.source.id
        } else {
            key
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .animateItemPlacement()
                .selectedBackground(!errorMessageMap.values.flatten().fastAny { !it.selected })
                .combinedClickable(
                    onClick = {
                        val categoryKey = Pair(id, null)
                        if (!expanded.containsKey(categoryKey)) {
                            expanded[categoryKey] = false
                        }
                        expanded[categoryKey] = !expanded[categoryKey]!!
                    },
                    onLongClick = { onGroupSelected(errorMessageMap.values.flatten()) },
                )
                .padding(
                    horizontal = 12.dp,
                    vertical = MaterialTheme.padding.small,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isSourceOrCategory == 1) {
                val item = errorMessageMap.values.flatten().find { it.source.name == id }!!.source
                val sourceLangString =
                    LocaleHelper.getSourceDisplayName(item.lang, LocalContext.current)
                        .takeIf { showLanguageInContent }
                val icon = returnSourceIcon(item.id)
                if (icon != null) {
                    Image(
                        bitmap = icon,
                        contentDescription = null,
                        modifier = Modifier
                            .height(50.dp)
                            .aspectRatio(1f),
                    )
                }
                Column(
                    modifier = Modifier
                        .padding(horizontal = MaterialTheme.padding.medium)
                        .weight(1f),
                ) {
                    Text(
                        text = item.name.ifBlank { item.id.toString() },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.Medium,
                        fontSize = 16.sp,
                        lineHeight = 24.sp,
                        letterSpacing = 0.15.sp,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (sourceLangString != null) {
                            Text(
                                modifier = Modifier.secondaryItemAlpha(),
                                text = sourceLangString,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
                val mangaCount = errorMessageMap.values.flatten().size
                val sourceCount = sourcesCount.find { it.first.id == item.id }!!.second
                val pillAlpha = if (isSystemInDarkTheme()) 0.12f else 0.08f
                Pill(
                    text = "$mangaCount/$sourceCount",
                    modifier = Modifier.padding(start = 4.dp),
                    color = MaterialTheme.colorScheme.onBackground
                        .copy(alpha = pillAlpha),
                    fontSize = 14.sp,
                )
                val rotation by animateFloatAsState(
                    targetValue = if (expanded[Pair(id, null)] == true) 0f else -180f,
                    animationSpec = tween(500),
                    label = "",
                )
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowUp,
                    modifier = Modifier
                        .rotate(rotation)
                        .padding(vertical = 8.dp, horizontal = 16.dp),
                    contentDescription = null,
                )
            } else if (isSourceOrCategory == 2) {
                Column(
                    modifier = Modifier
                        .padding(horizontal = MaterialTheme.padding.small)
                        .weight(1f),
                ) {
                    Text(
                        id,
                        fontWeight = FontWeight.Medium,
                        fontSize = 20.sp,
                        lineHeight = 24.sp,
                        letterSpacing = 0.15.sp,
                    )
                }
                val mangaCount = errorMessageMap.values.flatten().size
                val pillAlpha = if (isSystemInDarkTheme()) 0.12f else 0.08f
                Pill(
                    text = "$mangaCount",
                    modifier = Modifier.padding(start = 4.dp),
                    color = MaterialTheme.colorScheme.onBackground
                        .copy(alpha = pillAlpha),
                    fontSize = 14.sp,
                )
                val rotation by animateFloatAsState(
                    targetValue = if (expanded[Pair(id, null)] == true) 0f else -180f,
                    animationSpec = tween(500),
                    label = "",
                )
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowUp,
                    modifier = Modifier
                        .rotate(rotation)
                        .padding(vertical = 8.dp, horizontal = 25.dp),
                    contentDescription = null,
                )
            }
        }
    }
    errorMessageMap.forEach { (errorMessage, items) ->
        val errorMessageHeaderId =
            Pair(id, errorMessage)
        stickyHeader(
            key =
            errorMessageHeaderId.hashCode(),
        ) {
            AnimatedVisibility(modifier = Modifier.animateItemPlacement(), visible = expanded[Pair(id, null)] == true) {
                Row(
                    modifier =
                    Modifier
                        .fillMaxWidth()
                        .animateItemPlacement()
                        .selectedBackground(!items.fastAny { !it.selected })
                        .combinedClickable(
                            onClick =
                            {
                                expanded[errorMessageHeaderId] =
                                    if (expanded[errorMessageHeaderId] ==
                                        null
                                    ) {
                                        false
                                    } else {
                                        !expanded[errorMessageHeaderId]!!
                                    }
                            },
                            onLongClick =
                            { onGroupSelected(items) },
                        )
                        .padding(
                            start = 30.dp,
                            top = MaterialTheme.padding.small,
                            bottom = MaterialTheme.padding.small,
                            end = MaterialTheme.padding.small,
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .animateContentSize(),
                    ) {
                        Text(
                            errorMessage ?: "Null",
                            maxLines = if (expanded[errorMessageHeaderId] == true) Int.MAX_VALUE else 1,
                            color = MaterialTheme.colorScheme.error,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp,
                            lineHeight = 24.sp,
                            letterSpacing = 0.15.sp,
                        )
                    }
                    val rotation by animateFloatAsState(
                        targetValue = if (expanded[errorMessageHeaderId] == true) 0f else -180f,
                        animationSpec = tween(500),
                        label = "",
                    )
                    Box(
                        modifier = Modifier
                            .padding(top = 8.dp, bottom = 8.dp, start = 10.dp, end = 18.dp)
                            .rotate(rotation),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.KeyboardArrowUp,
                            contentDescription = null,
                        )
                    }
                }
            }
        }

        itemsIndexed(items, key = { _, item -> item.libraryManga.manga.id }) { _, item ->
            AnimatedVisibility(
                modifier = Modifier.animateItemPlacement(),
                visible = expanded[errorMessageHeaderId] == true && expanded[Pair(id, null)] == true,
            ) {
                FailedUpdatesUiItem(
                    modifier = Modifier,
                    selected = item.selected,
                    onLongClick = {
                        onSelected(item, !item.selected, true, true)
                    },
                    onClick = {
                        when {
                            selectionMode -> onSelected(item, !item.selected, true, false)
                            else -> onMangaClick(item)
                        }
                    },
                    manga = item,
                    onMigrateManga = { onMigrateManga(item) }.takeIf { !selectionMode },
                    padding = 34.dp,
                )
            }
        }
    }
}

@Composable
fun MigrateIndicator(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    TextButton(
        onClick = onClick,
        modifier = modifier,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Outlined.FlightTakeoff,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.height(3.3.dp))
            Text(
                text = stringResource(R.string.action_migrate),
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
fun CategoryList(
    contentPadding: PaddingValues,
    selectionMode: Boolean,
    onMangaClick: (FailedUpdatesManga) -> Unit,
    onMigrateManga: (FailedUpdatesManga) -> Unit,
    onGroupSelected: (List<FailedUpdatesManga>) -> Unit,
    onSelected: (FailedUpdatesManga, Boolean, Boolean, Boolean) -> Unit,
    categoryMap: Map<String, Map<String?, List<FailedUpdatesManga>>>,
    isSourceOrCategory: Int,
    expanded: MutableMap<Pair<String, String?>, Boolean>,
    sourcesCount: List<Pair<Source, Long>>,
) {
    FastScrollLazyColumn(contentPadding = contentPadding, modifier = Modifier.fillMaxHeight()) {
        categoryMap.forEach { (category, errorMessageMap) ->
            failedUpdatesGroupUiItem(
                id = category,
                errorMessageMap = errorMessageMap,
                selectionMode = selectionMode,
                onMangaClick = onMangaClick,
                onSelected = onSelected,
                onMigrateManga = onMigrateManga,
                onGroupSelected = onGroupSelected,
                expanded = expanded,
                isSourceOrCategory = isSourceOrCategory,
                sourcesCount = sourcesCount,
            )
        }
    }
}
