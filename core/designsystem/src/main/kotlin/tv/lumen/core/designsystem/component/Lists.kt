package tv.lumen.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridItemScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import tv.lumen.core.designsystem.focus.FocusMemory
import tv.lumen.core.designsystem.focus.rememberFocus
import tv.lumen.core.designsystem.theme.LumenDimens

/** Подгрузка следующей страницы, когда до конца остаётся столько карточек (SPEC 8). */
const val LOAD_MORE_THRESHOLD = 6

/** Горизонтальный ряд карточек с заголовком. [rowKey] отличает ряды в памяти фокуса. */
@Composable
fun <T> ContentRow(
    title: String,
    items: List<T>,
    key: (T) -> String,
    focusMemory: FocusMemory,
    rowKey: String,
    modifier: Modifier = Modifier,
    isFirstRow: Boolean = false,
    onLoadMore: (() -> Unit)? = null,
    itemContent: @Composable (item: T, modifier: Modifier) -> Unit,
) {
    val state = rememberLazyListState()
    if (onLoadMore != null) {
        val nearEnd by remember(items.size) {
            derivedStateOf { (state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >= items.size - LOAD_MORE_THRESHOLD }
        }
        LaunchedEffect(nearEnd, items.size) { if (nearEnd && items.isNotEmpty()) onLoadMore() }
    }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(start = LumenDimens.SafeHorizontal),
        )
        LazyRow(
            state = state,
            contentPadding = PaddingValues(horizontal = LumenDimens.SafeHorizontal, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.focusRestorer(),
        ) {
            itemsIndexed(items, key = { _, item -> key(item) }) { index, item ->
                itemContent(
                    item,
                    Modifier
                        .width(CardWidth)
                        .rememberFocus(focusMemory, "$rowKey/${key(item)}", isFirst = isFirstRow && index == 0),
                )
            }
        }
    }
}

/** Сетка карточек с подгрузкой следующей страницы. */
@Composable
fun <T> PagedGrid(
    items: List<T>,
    key: (T) -> String,
    focusMemory: FocusMemory,
    modifier: Modifier = Modifier,
    columns: Int = 3,
    canLoadMore: Boolean = false,
    onLoadMore: () -> Unit = {},
    header: (@Composable () -> Unit)? = null,
    itemContent: @Composable LazyGridItemScope.(item: T, modifier: Modifier) -> Unit,
) {
    val state = rememberLazyGridState()
    val nearEnd by remember(items.size) {
        derivedStateOf { (state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >= items.size - LOAD_MORE_THRESHOLD }
    }
    LaunchedEffect(nearEnd, items.size, canLoadMore) { if (nearEnd && canLoadMore) onLoadMore() }

    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        state = state,
        contentPadding = PaddingValues(horizontal = LumenDimens.SafeHorizontal, vertical = LumenDimens.SafeVertical),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier.focusRestorer(),
    ) {
        if (header != null) {
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }, key = "header") { header() }
        }
        itemsIndexed(items, key = { _, item -> key(item) }) { index, item ->
            itemContent(item, Modifier.rememberFocus(focusMemory, key(item), isFirst = index == 0))
        }
    }
}