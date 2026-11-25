package dev.fadalias.ui.screens

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
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.fadalias.R
import dev.fadalias.FadaliasViewModel
import dev.fadalias.data.AppFilter
import dev.fadalias.data.AppItem
import dev.fadalias.data.supported
import dev.fadalias.ui.components.AppIcon
import dev.fadalias.ui.components.SharedCtx
import dev.fadalias.ui.components.SortMenu
import dev.fadalias.ui.components.SystemAppsWarning
import dev.fadalias.ui.components.StatusBadges
import dev.fadalias.ui.components.sortLabel

@Composable
fun AppsScreen(
    vm: FadaliasViewModel,
    listState: LazyListState,
    shared: SharedCtx,
    onOpenApp: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var showSort by remember { mutableStateOf(false) }
    var showSystemWarning by remember { mutableStateOf(false) }
    val shown by remember { derivedStateOf { vm.filteredApps() } }

    Scaffold(
        modifier = modifier.nestedScroll(scroll.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.apps_title)) },
                actions = {
                    androidx.compose.foundation.layout.Box {
                        IconButton(onClick = { showSort = true }) {
                            Icon(Icons.AutoMirrored.Rounded.Sort, contentDescription = stringResource(R.string.sort_title))
                        }
                        SortMenu(
                            expanded = showSort,
                            onDismiss = { showSort = false },
                            current = vm.sort,
                            onSelect = { vm.sort = it },
                            showSystem = vm.showSystemApps,
                            onShowSystemChange = { on ->
                                if (!on) vm.showSystemApps = false
                                else if (vm.systemWarningAccepted) vm.showSystemApps = true
                                else showSystemWarning = true
                            },
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background,
                ),
                scrollBehavior = scroll,
            )
        },
    ) { pad ->
        PullToRefreshBox(
            isRefreshing = vm.refreshing,
            onRefresh = { vm.refresh() },
            modifier = Modifier.fillMaxSize().padding(top = pad.calculateTopPadding()),
        ) {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(bottom = pad.calculateBottomPadding() + 120.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                item(key = "search") {
                    SearchField(
                        query = vm.query,
                        onQuery = { vm.query = it },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
                item(key = "filters") {
                    FilterRow(vm, modifier = Modifier.padding(vertical = 8.dp))
                }
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
                        shared = shared,
                        onClick = { onOpenApp(app.key) },
                        modifier = Modifier.animateItem().padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
            }
        }
    }

    if (showSystemWarning) {
        SystemAppsWarning(
            onCancel = { showSystemWarning = false }, // cancelled: the next attempt shows the warning again
            onConfirm = {
                vm.systemWarningAccepted = true
                vm.showSystemApps = true
                showSystemWarning = false
            },
        )
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
private fun FilterRow(vm: FadaliasViewModel, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = vm.filters.isEmpty(),
            onClick = { vm.clearFilters() },
            label = { Text(stringResource(R.string.filter_all)) },
            leadingIcon = if (vm.filters.isEmpty()) {
                { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
            } else {
                null
            },
        )
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
    }
}

@Composable
private fun AppRow(app: AppItem, shared: SharedCtx, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = if (app.hidden) scheme.surfaceContainerLow else scheme.surfaceContainerHigh,
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            AppIcon(app, 52.dp, shared = shared, dimmed = app.hidden)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = app.displayLabel,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (app.hidden) scheme.onSurfaceVariant else scheme.onSurface,
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
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            StatusBadges(app)
        }
    }
}

fun formatSize(mb: Float): String =
    if (mb >= 1000f) String.format(java.util.Locale.getDefault(), "%.1f GB", mb / 1024f)
    else String.format(java.util.Locale.getDefault(), "%.0f MB", mb)
