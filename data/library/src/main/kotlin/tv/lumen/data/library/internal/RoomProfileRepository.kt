package tv.lumen.data.library.internal

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import tv.lumen.core.common.IoDispatcher
import tv.lumen.core.database.ProfileDao
import tv.lumen.core.database.ProfileEntity
import tv.lumen.core.database.VideoReactionDao
import tv.lumen.core.database.VideoReactionEntity
import tv.lumen.core.datastore.SettingsRepository
import tv.lumen.core.model.Profile
import tv.lumen.core.model.Reaction
import tv.lumen.data.library.ProfileRepository
import javax.inject.Inject
import javax.inject.Singleton

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
internal class RoomProfileRepository @Inject constructor(
    private val profileDao: ProfileDao,
    private val reactionDao: VideoReactionDao,
    private val settings: SettingsRepository,
    @IoDispatcher private val io: CoroutineDispatcher,
) : ProfileRepository {

    private val createLock = Mutex()

    override val profiles: Flow<List<Profile>> = profileDao.observeAll()
        .onStart { ensureDefault() }
        .map { list -> list.map { it.toModel() } }
        .flowOn(io)

    override val current: Flow<Profile> = combine(profiles, settings.flow { it.currentProfileId }) { list, id ->
        list.firstOrNull { it.id == id } ?: list.firstOrNull()
    }.map { it ?: Profile(0, "") }.distinctUntilChanged()

    override suspend fun create(name: String): Profile = withContext(io) {
        val id = profileDao.insert(ProfileEntity(name = name.trim(), createdAt = System.currentTimeMillis()))
        settings.set { it.copy(currentProfileId = id) }
        Profile(id, name.trim())
    }

    override suspend fun select(id: Long) {
        settings.set { it.copy(currentProfileId = id) }
    }

    override suspend fun rename(id: Long, name: String) = withContext(io) { profileDao.rename(id, name.trim()) }

    override suspend fun delete(id: Long) = withContext(io) {
        val all = profileDao.getAll()
        if (all.size <= 1) return@withContext
        profileDao.delete(id)
        if (settings.current.value.currentProfileId == id) {
            settings.set { s -> s.copy(currentProfileId = all.first { it.id != id }.id) }
        }
    }

    override fun reaction(videoId: String): Flow<Reaction?> = current
        .map { it.id }
        .distinctUntilChanged()
        .flatMapLatest { profileId -> reactionDao.observe(profileId, videoId) }
        .map { value -> when (value) { 1 -> Reaction.LIKE; -1 -> Reaction.DISLIKE; else -> null } }
        .flowOn(io)

    override suspend fun setReaction(videoId: String, reaction: Reaction?) = withContext(io) {
        val profileId = currentId()
        if (reaction == null) {
            reactionDao.delete(profileId, videoId)
        } else {
            val value = if (reaction == Reaction.LIKE) 1 else -1
            reactionDao.upsert(VideoReactionEntity(profileId, videoId, value, System.currentTimeMillis()))
        }
    }

    private suspend fun currentId(): Long {
        ensureDefault()
        val all = profileDao.getAll()
        val id = settings.current.value.currentProfileId
        return all.firstOrNull { it.id == id }?.id ?: all.first().id
    }

    /** Первый запуск: создать профиль по умолчанию (пустое имя — подпись берёт UI). */
    private suspend fun ensureDefault() = createLock.withLock {
        if (profileDao.getAll().isEmpty()) {
            val id = profileDao.insert(ProfileEntity(name = "", createdAt = System.currentTimeMillis()))
            settings.set { it.copy(currentProfileId = id) }
        }
    }

    private fun ProfileEntity.toModel() = Profile(id, name)
}
