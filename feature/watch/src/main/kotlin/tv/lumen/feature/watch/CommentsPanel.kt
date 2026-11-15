package tv.lumen.feature.watch

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import tv.lumen.core.common.PagedState
import tv.lumen.core.designsystem.component.errorMessage
import tv.lumen.core.designsystem.theme.LumenDimens
import tv.lumen.core.model.Comment

/** Комментарии справа поверх видео; пульт листает их по одному (SPEC 5.5). */
@Composable
internal fun CommentsPanel(state: PagedState<Comment>?, onLoadMore: () -> Unit, modifier: Modifier = Modifier) {
    val list = rememberLazyListState()
    val first = remember { FocusRequester() }
    val items = state?.items.orEmpty()
    LaunchedEffect(items.isNotEmpty()) { if (items.isNotEmpty()) runCatching { first.requestFocus() } }
    val nearEnd by remember(items.size) { derivedStateOf { (list.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >= items.size - 4 } }
    LaunchedEffect(nearEnd, items.size) { if (nearEnd && state?.canLoadMore == true) onLoadMore() }

    Column(
        modifier = modifier.fillMaxHeight().width(560.dp).background(Color(0xF01A1A1A))
            .padding(vertical = LumenDimens.SafeVertical, horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.watch_comments), style = MaterialTheme.typography.headlineSmall, color = Color.White)
        when {
            state == null || state.isLoading -> Text(stringResource(R.string.watch_loading), color = Color.White)
            state.error != null -> Text(errorMessage(state.error!!), color = Color.White)
            items.isEmpty() -> Text(stringResource(R.string.watch_comments_empty), color = Color.White)
            else -> LazyColumn(state = list, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                itemsIndexed(items, key = { _, c -> c.id }) { index, c ->
                    ListItem(
                        selected = false,
                        onClick = {},
                        overlineContent = { Text(listOfNotNull(c.authorName, c.publishedText).joinToString(" · ")) },
                        headlineContent = { Text(c.text, maxLines = 8) },
                        supportingContent = if (c.likeCount > 0) ({ Text("♥ ${c.likeCount}") }) else null,
                        modifier = if (index == 0) Modifier.focusRequester(first) else Modifier,
                    )
                }
            }
        }
    }
}