package suwayomi.tachidesk.graphql.mutations

import io.javalin.http.UploadedFile
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import suwayomi.tachidesk.graphql.server.TemporaryFileStorage
import suwayomi.tachidesk.graphql.types.BackupRestoreState
import suwayomi.tachidesk.graphql.types.BackupRestoreStatus
import suwayomi.tachidesk.graphql.types.toStatus
import suwayomi.tachidesk.manga.impl.backup.BackupFlags
import suwayomi.tachidesk.manga.impl.backup.proto.ProtoBackupExport
import suwayomi.tachidesk.manga.impl.backup.proto.ProtoBackupImport
import suwayomi.tachidesk.server.JavalinSetup.future
import java.util.concurrent.CompletableFuture
import kotlin.time.Duration.Companion.seconds

class BackupMutation {
    data class RestoreBackupInput(
        val clientMutationId: String? = null,
        val backup: UploadedFile
    )
    data class RestoreBackupPayload(
        val clientMutationId: String?,
        val status: BackupRestoreStatus
    )

    @OptIn(DelicateCoroutinesApi::class)
    fun restoreBackup(
        input: RestoreBackupInput
    ): CompletableFuture<RestoreBackupPayload> {
        val (clientMutationId, backup) = input

        return future {
            GlobalScope.launch {
                ProtoBackupImport.performRestore(backup.content)
            }

            val status = withTimeout(10.seconds) {
                ProtoBackupImport.backupRestoreState.first {
                    it != ProtoBackupImport.BackupRestoreState.Idle
                }.toStatus()
            }

            RestoreBackupPayload(clientMutationId, status)
        }
    }

    data class CreateBackupInput(
        val clientMutationId: String? = null,
        val includeChapters: Boolean? = null,
        val includeCategories: Boolean? = null
    )
    data class CreateBackupPayload(
        val clientMutationId: String?,
        val url: String
    )
    fun createBackup(
        input: CreateBackupInput? = null
    ): CreateBackupPayload {
        val filename = ProtoBackupExport.getBackupFilename()

        val backup = ProtoBackupExport.createBackup(
            BackupFlags(
                includeManga = true,
                includeCategories = input?.includeCategories ?: true,
                includeChapters = input?.includeChapters ?: true,
                includeTracking = true,
                includeHistory = true
            )
        )

        TemporaryFileStorage.saveFile(filename, backup)

        return CreateBackupPayload(
            clientMutationId = input?.clientMutationId,
            url = "/api/graphql/files/backup/$filename"
        )
    }
}
