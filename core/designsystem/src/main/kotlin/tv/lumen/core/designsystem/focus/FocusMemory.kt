package tv.lumen.core.designsystem.focus

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged

/**
 * Запоминает, какой элемент экрана был в фокусе, и возвращает фокус на него при возврате на экран
 * (SPEC 8: «фокус никогда не теряется»). Живёт в ViewModel экрана, поэтому переживает переход вперёд-назад.
 *
 * При первом входе фокус получает элемент, помеченный `isFirst` (первая карточка).
 */
class FocusMemory {
    internal var key: String? = null
    internal var armed: Boolean = true

    // Меняется при [restore], чтобы элементы заново попробовали взять фокус
    internal var restoreTick by mutableIntStateOf(0)

    /** Забыть позицию: например, после нового поиска фокус снова на первую карточку. */
    fun reset() {
        key = null
        armed = true
    }

    /**
     * Вернуть фокус на запомненный элемент (или на [toKey]) — например, после закрытия диалога,
     * иначе фокус уходит в боковое меню.
     */
    fun restore(toKey: String? = null) {
        if (toKey != null) key = toKey
        armed = true
        restoreTick++
    }

    internal fun consume(itemKey: String, isFirst: Boolean): Boolean {
        if (!armed) return false
        val match = if (key == null) isFirst else key == itemKey
        if (match) armed = false
        return match
    }
}

/** Вызывается один раз на экране: при уходе с экрана память снова «взводится». */
@Composable
fun FocusMemoryScope(memory: FocusMemory) {
    DisposableEffect(memory) { onDispose { memory.armed = true } }
}

/** Модификатор элемента списка: запоминает фокус и восстанавливает его при входе на экран. */
@Composable
fun Modifier.rememberFocus(memory: FocusMemory, key: String, isFirst: Boolean = false): Modifier {
    val requester = remember { FocusRequester() }
    LaunchedEffect(key, memory.restoreTick) {
        if (memory.consume(key, isFirst)) runCatching { requester.requestFocus() }
    }
    return this
        .focusRequester(requester)
        .onFocusChanged { if (it.isFocused) memory.key = key }
}