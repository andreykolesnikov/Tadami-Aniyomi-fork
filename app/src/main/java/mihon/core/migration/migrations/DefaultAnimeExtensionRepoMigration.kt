package mihon.core.migration.migrations

import logcat.LogPriority
import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext
import mihon.domain.extensionrepo.anime.interactor.CreateAnimeExtensionRepo
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat

class DefaultAnimeExtensionRepoMigration : Migration {
    override val version = Migration.ALWAYS

    override suspend fun invoke(migrationContext: MigrationContext): Boolean = withIOContext {
        val createAnimeExtensionRepo = migrationContext.get<CreateAnimeExtensionRepo>()
            ?: return@withIOContext false

        val result = createAnimeExtensionRepo.await(DEFAULT_ANIME_REPO_URL)
        
        when (result) {
            is CreateAnimeExtensionRepo.Result.Success -> {
                logcat(LogPriority.INFO) { "Successfully added default anime extension repo" }
            }
            is CreateAnimeExtensionRepo.Result.RepoAlreadyExists -> {
                logcat(LogPriority.INFO) { "Default anime extension repo already exists" }
            }
            else -> {
                logcat(LogPriority.WARN) { "Failed to add default anime extension repo: $result" }
            }
        }

        return@withIOContext true
    }

    companion object {
        const val DEFAULT_ANIME_REPO_URL = "https://kohiden.xyz/Kohi-den/extensions/raw/branch/main/index.min.json"
    }
}
