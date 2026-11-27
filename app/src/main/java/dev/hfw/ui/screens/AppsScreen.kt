package dev.hfw.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import dev.hfw.data.AppSort
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.selected as semanticsSelected
import androidx.compose.ui.semantics.semantics
import androidx.compose.material3.Switch
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.hfw.R
import dev.hfw.HfwViewModel
import dev.hfw.data.AppFilter
import dev.hfw.data.AppItem
import dev.hfw.data.supported
import dev.hfw.ui.components.AppIcon
import dev.hfw.ui.components.IconTrack
import dev.hfw.ui.components.SortMenuButton
import dev.hfw.ui.components.SystemAppsWarning
import dev.hfw.ui.components.StatusBadges
import dev.hfw.ui.components.LocalListBottomPadding
import dev.hfw.ui.components.LocalListTopPadding
import dev.hfw.ui.components.pressScale
import dev.hfw.ui.components.staggerIn
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.LaunchedEffect
import dev.hfw.ui.components.sortLabel

@Composable
fun AppsScreen(
    vm: HfwViewModel,
    listState: LazyListState,
    onOpenApp: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shown by remember { derivedStateOf { vm.filteredApps() } }
    // The shell's header offers "select all" for whatever is currently listed.
    LaunchedEffect(shown) { vm.selectableKeys = shown.map { it.key } }

    run {
        PullToRefreshBox(
            isRefreshing = vm.refreshing,
            onRefresh = { vm.refresh() },
            modifier = modifier.fillMaxSize(),
        ) {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(top = LocalListTopPadding.current, bottom = LocalListBottomPadding.current),
                modifier = Modifier.fillMaxSize(),
            ) {
                item(key = "count") {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.apps_count_fmt, shown.size),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = sortLabel(vm.sort),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                if (shown.isEmpty()) {
                    item(key = "empty") {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(48.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Icon(
                                Icons.Rounded.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = stringResource(R.string.apps_empty),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
                items(shown, key = { it.key }) { app ->
                    AppRow(
                        app = app,
                        selecting = vm.selecting,
                        selected = app.key in vm.selection,
                        onLongClick = { vm.toggleSelect(app.key) },
                        onClick = { if (vm.selecting) vm.toggleSelect(app.key) else onOpenApp(app.key) },
                        modifier = Modifier.animateItem().padding(horizontal = 14.dp, vertical = 3.dp),
                    )
                }
            }
        }
    }
}

/** Search + sort button + filter chips. Hosted by the shell so it can slide away together with the header. */
@Composable
fun AppsToolbar(vm: HfwViewModel, onSystemWarning: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SearchField(query = vm.query, onQuery = { vm.query = it }, modifier = Modifier.weight(1f))
                if (!vm.selecting) {
                Spacer(Modifier.width(8.dp))
                SortMenuButton(
                    title = stringResource(R.string.sort_title),
                    options = AppSort.entries.map { it to sortLabel(it) },
                    selected = vm.sort,
                    highlighted = vm.sort != AppSort.CHANGED_FIRST || vm.showSystemApps,
                    onSelect = { vm.sort = it },
                    footer = { close ->
                        HorizontalDivider(Modifier.padding(vertical = 4.dp, horizontal = 8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 6.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    if (vm.showSystemApps) {
                                        vm.showSystemApps = false
                                    } else if (vm.systemWarningAccepted) {
                                        vm.showSystemApps = true
                                    } else {
                                        close()
                                        onSystemWarning()
                                    }
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f).padding(end = 8.dp)) {
                                Text(stringResource(R.string.show_system_apps), style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    stringResource(R.string.show_system_apps_sub),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(checked = vm.showSystemApps, onCheckedChange = null)
                        }
                    },
                )
                }
            }

        // While selecting, only the search field stays.
        if (!vm.selecting) FilterRow(vm, modifier = Modifier.padding(top = 8.dp))
        Spacer(Modifier.height(14.dp))
    }
}

@Composable
private fun SearchField(query: String, onQuery: (String) -> Unit, modifier: Modifier = Modifier) {
    val transparent = Color.Transparent
    TextField(
        value = query,
        onValueChange = onQuery,
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        shape = CircleShape,
        placeholder = { Text(stringResource(R.string.search_hint)) },
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQuery("") }) {
                    Icon(Icons.Rounded.Close, contentDescription = null)
                }
            }
        },
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            focusedIndicatorColor = transparent,
            unfocusedIndicatorColor = transparent,
            disabledIndicatorColor = transparent,
            errorIndicatorColor = transparent,
        ),
    )
}

@Composable
private fun FilterRow(vm: HfwViewModel, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = vm.filters.isEmpty() && vm.profileFilters.isEmpty(),
            onClick = { vm.clearFilters() },
            label = { Text(stringResource(R.string.filter_all)) },
            leadingIcon = if (vm.filters.isEmpty() && vm.profileFilters.isEmpty()) {
                { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
            } else {
                null
            },
        )
        val profiles = vm.availableProfiles
        listOf(
            AppFilter.ICON_CHANGED to R.string.filter_icon,
            AppFilter.RENAMED to R.string.filter_renamed,
            AppFilter.HIDDEN to R.string.filter_hidden,
        ).filter { it.first.supported() }.forEach { (filter, label) ->
            val selected = filter in vm.filters
            FilterChip(
                selected = selected,
                onClick = { vm.toggleFilter(filter) },
                label = { Text(stringResource(label)) },
                leadingIcon = if (selected) {
                    { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
                } else {
                    null
                },
            )
        }
        if (profiles.size > 1) {
            profiles.forEach { p ->
                val selected = p in vm.profileFilters
                FilterChip(
                    selected = selected,
                    onClick = { vm.toggleProfileFilter(p) },
                    label = { Text(stringResource(profileLabel(p))) },
                    leadingIcon = if (selected) {
                        { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
                    } else {
                        null
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppRow(
    app: AppItem,
    selecting: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(20.dp)
    val source = remember { MutableInteractionSource() }
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .pressScale(source)
            .clip(shape)
            .semantics { if (selecting) semanticsSelected = selected }
            .combinedClickable(interactionSource = source, indication = androidx.compose.material3.ripple(), onClick = onClick, onLongClick = onLongClick),
        shape = shape,
        color = when {
            selected -> scheme.primaryContainer
            app.hidden -> scheme.surfaceContainerLow
            else -> scheme.surfaceContainerHigh
        },
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            AppIcon(app, 52.dp, track = IconTrack.ROW, dimmed = app.hidden)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = app.displayLabel,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (app.hidden && !selected) scheme.onSurfaceVariant else if (selected) scheme.onPrimaryContainer else scheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (app.renamed) {
                    Text(
                        text = stringResource(R.string.original_name_fmt, app.label),
                        style = MaterialTheme.typography.labelMedium,
                        color = scheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = "${formatSize(app.sizeMb)} · ${app.pkg}" + if (app.userId != 0) " · #${app.userId}" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (selected) scheme.onPrimaryContainer.copy(alpha = 0.8f) else scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (selecting) {
                Icon(
                    imageVector = if (selected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = if (selected) scheme.primary else scheme.outline,
                )
            } else {
                StatusBadges(app)
            }
        }
    }
}

fun formatSize(mb: Float): String =
    if (mb >= 1000f) String.format(java.util.Locale.getDefault(), "%.1f GB", mb / 1024f)
    else String.format(java.util.Locale.getDefault(), "%.0f MB", mb)
