package tv.lumen.core.common

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import tv.lumen.core.model.AppError
import tv.lumen.core.model.AppResult
import tv.lumen.core.model.Page
import tv.lumen.core.model.PageToken

@OptIn(ExperimentalCoroutinesApi::class)
class PagerTest {

    private val pages = mapOf(
        null to Page(listOf("a", "b"), PageToken("2")),
        "2" to Page(listOf("b", "c"), PageToken("3")),
        "3" to Page(listOf("d"), null),
    )

    @Test
    fun `страницы склеиваются без дублей и заканчиваются`() = runTest(UnconfinedTestDispatcher()) {
        val pager = Pager<String>(this, { it }) { token -> AppResult.Success(pages.getValue(token?.value)) }
        pager.refresh()
        assertEquals(listOf("a", "b"), pager.state.value.items)
        pager.loadMore()
        assertEquals(listOf("a", "b", "c"), pager.state.value.items)
        pager.loadMore()
        assertEquals(listOf("a", "b", "c", "d"), pager.state.value.items)
        assertNull(pager.state.value.next)
        assertFalse(pager.state.value.canLoadMore)
    }

    @Test
    fun `ошибка первой страницы и повтор`() = runTest(UnconfinedTestDispatcher()) {
        var fail = true
        val pager = Pager<String>(this, { it }) { token ->
            if (fail) AppResult.Failure(AppError.Network) else AppResult.Success(pages.getValue(token?.value))
        }
        pager.refresh()
        assertEquals(AppError.Network, pager.state.value.error)
        fail = false
        pager.refresh()
        assertNull(pager.state.value.error)
        assertEquals(2, pager.state.value.items.size)
    }

    @Test
    fun `ошибка подгрузки не стирает список`() = runTest(UnconfinedTestDispatcher()) {
        var calls = 0
        val pager = Pager<String>(this, { it }) { token ->
            calls++
            if (token != null) AppResult.Failure(AppError.Network) else AppResult.Success(pages.getValue(null))
        }
        pager.refresh()
        pager.loadMore()
        assertEquals(listOf("a", "b"), pager.state.value.items)
        assertNull(pager.state.value.error)
        assertEquals(2, calls)
    }
}
