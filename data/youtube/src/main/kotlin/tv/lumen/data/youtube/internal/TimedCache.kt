package tv.lumen.data.youtube.internal

/** Небольшой потокобезопасный кэш в памяти с временем жизни записи и ограничением размера (LRU). */
internal class TimedCache<K : Any, V : Any>(
    private val ttlMillis: Long,
    private val maxSize: Int,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private class Entry<V>(val value: V, val storedAt: Long)

    private val map = object : LinkedHashMap<K, Entry<V>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, Entry<V>>?) = size > maxSize
    }

    @Synchronized
    fun get(key: K): V? {
        val entry = map[key] ?: return null
        if (clock() - entry.storedAt >= ttlMillis) {
            map.remove(key)
            return null
        }
        return entry.value
    }

    @Synchronized
    fun put(key: K, value: V) {
        map[key] = Entry(value, clock())
    }

    @Synchronized
    fun remove(key: K) {
        map.remove(key)
    }
}
