package tv.lumen.core.common

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tv.lumen.core.model.AppError
import tv.lumen.core.model.AppResult
import tv.lumen.core.model.Page
import tv.lumen.core.model.PageToken

data class PagedState<T>(
    val items: List<T> = emptyList(),
    /** Первая страница ещё грузится. */
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    /** Ошибка первой страницы — экран показывает «Повторить». Ошибка следующих страниц не мешает списку. */
    val error: AppError? = null,
    val next: PageToken? = null,
) {
    val canLoadMore: Boolean get() = next != null && !isLoadingMore && !isLoading
}

/**
 * Постраничная загрузка для экранов-списков: первая страница, подгрузка следующих, повтор.
 * Дубликаты по [key] отбрасываются — YouTube иногда повторяет элементы на соседних страницах.
 */
class Pager<T>(
    private val scope: CoroutineScope,
    private val key: (T) -> String,
    private val load: suspend (PageToken?) -> AppResult<Page<T>>,
) {
    private val _state = MutableStateFlow(PagedState<T>())
    val state: StateFlow<PagedState<T>> = _state.asStateFlow()
    private var job: Job? = null

    fun refresh() {
        job?.cancel()
        _state.value = PagedState(isLoading = true)
        job = scope.launch {
            when (val result = load(null)) {
                is AppResult.Success -> _state.value = PagedState(
                    items = result.value.items.distinctBy(key),
                    isLoading = false,
                    next = result.value.next,
                )
                is AppResult.Failure -> _state.value = PagedState(isLoading = false, error = result.error)
            }
        }
    }

    fun loadMore() {
        val current = _state.value
        val token = current.next ?: return
        if (!current.canLoadMore) return
        _state.update { it.copy(isLoadingMore = true) }
        job = scope.launch {
            when (val result = load(token)) {
                is AppResult.Success -> _state.update { s ->
                    val seen = s.items.mapTo(HashSet(), key)
                    s.copy(
                        items = s.items + result.value.items.filter { seen.add(key(it)) },
                        isLoadingMore = false,
                        next = result.value.next,
                    )
                }
                // Ошибка подгрузки: оставляем загруженное, следующую попытку сделает прокрутка
                is AppResult.Failure -> _state.update { it.copy(isLoadingMore = false) }
            }
        }
    }
}
