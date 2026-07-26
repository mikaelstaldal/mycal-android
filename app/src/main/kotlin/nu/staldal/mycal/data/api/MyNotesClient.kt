package nu.staldal.mycal.data.api


import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import androidx.core.net.toUri

/**
 * Reads notes from the **MyNotes app installed on this device**, through the content provider that
 * app exports.
 *
 * MyCal is not a notes client: it does not talk to the MyNotes server, hold MyNotes credentials, or
 * keep a copy of any note. It asks the MyNotes app, which already syncs notes into a local database
 * and already knows how to work offline — so a note linked to an event is readable exactly when
 * MyNotes has it, with or without a network.
 *
 * Everything below mirrors `NotesContract` in the mynotes-android repo. Those strings are a
 * published interface between two separately-built apps, so they are duplicated here rather than
 * shared: keep them in step with that file.
 *
 * Access is granted by [PERMISSION_READ_NOTES], which MyNotes declares `signature` — so this only
 * works when both apps are signed with the same key. When they are not (or MyNotes is not
 * installed) [availability] says so and the integration stays hidden.
 */
object MyNotesClient {

    private const val LOGTAG = "MyNotesClient"

    const val AUTHORITY = "nu.staldal.mynotes.notes"
    const val PACKAGE = "nu.staldal.mynotes"
    const val PERMISSION_READ_NOTES = "nu.staldal.mynotes.permission.READ_NOTES"

    private const val PATH_NOTES = "notes"
    private const val PATH_TAGS = "tags"
    private const val PATH_ARTIFACTS = "artifacts"
    private const val PATH_LOCAL_ARTIFACTS = "local-artifacts"

    private const val PARAM_TITLE_PREFIX = "titlePrefix"
    private const val PARAM_LIMIT = "limit"

    private const val COLUMN_SLUG = "slug"
    private const val COLUMN_TITLE = "title"
    private const val COLUMN_CONTENT = "content"
    private const val COLUMN_HAS_FULL_CONTENT = "has_full_content"

    private val BASE_URI: Uri = "content://$AUTHORITY".toUri()

    /** Why the integration is or is not usable — the difference matters to the user. */
    enum class Availability {
        /** MyNotes is installed and readable. */
        AVAILABLE,

        /** No MyNotes app on this device. */
        NOT_INSTALLED,

        /** MyNotes is installed but did not grant access, which means mismatched signing keys. */
        NOT_PERMITTED,
        ;

        val isAvailable: Boolean get() = this == AVAILABLE
    }

    fun availability(context: Context): Availability = when {
        !isInstalled(context) -> Availability.NOT_INSTALLED
        context.checkSelfPermission(PERMISSION_READ_NOTES) != PackageManager.PERMISSION_GRANTED ->
            Availability.NOT_PERMITTED
        else -> Availability.AVAILABLE
    }

    /** Whether the MyNotes provider is on this device. Needs the `<queries>` entry in the manifest. */
    private fun isInstalled(context: Context): Boolean {
        val pm = context.packageManager
        val provider = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            pm.resolveContentProvider(AUTHORITY, PackageManager.ComponentInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            pm.resolveContentProvider(AUTHORITY, 0)
        }
        return provider != null
    }

    /**
     * Notes whose title starts with [prefix], for the note picker's autocomplete. Empty when
     * MyNotes is unavailable — the picker is hidden in that case, so this is not an error path.
     */
    fun searchNotes(context: Context, prefix: String, limit: Int = 10): List<NoteSummary> {
        val uri = BASE_URI.buildUpon()
            .appendPath(PATH_NOTES)
            .appendQueryParameter(PARAM_TITLE_PREFIX, prefix)
            .appendQueryParameter(PARAM_LIMIT, limit.toString())
            .build()
        return query(context, uri, arrayOf(COLUMN_SLUG, COLUMN_TITLE)) { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(NoteSummary(slug = cursor.getString(0), title = cursor.getString(1)))
                }
            }
        } ?: emptyList()
    }

    /**
     * One note with its content, or null when MyNotes does not have it (or is unavailable).
     * [Note.hasFullContent] is false when MyNotes has only synced the note's metadata and cannot
     * reach the server to fetch the body.
     */
    fun getNote(context: Context, slug: String): Note? {
        val uri = noteUri(slug)
        return query(context, uri, arrayOf(COLUMN_SLUG, COLUMN_TITLE, COLUMN_CONTENT, COLUMN_HAS_FULL_CONTENT)) { cursor ->
            if (!cursor.moveToFirst()) return@query null
            Note(
                slug = cursor.getString(0),
                title = cursor.getString(1),
                content = cursor.getString(2) ?: "",
                hasFullContent = cursor.getInt(3) != 0,
            )
        }
    }

    /**
     * An image a rendered note embeds, fetched from MyNotes' own artifact cache. [ref] is the
     * reference as it appears in note content — `/api/v1/artifacts/<sha256>` for an uploaded image,
     * `local-artifact://<id>` for one attached on this device and not yet uploaded.
     */
    fun fetchImage(context: Context, ref: String): NoteImage? {
        val uri = imageUri(ref) ?: return null
        val resolver = context.contentResolver
        return try {
            val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
            // Read the type after opening: MyNotes resolves the image on the first of the two calls
            // and answers the second from that result, so this order costs one resolution.
            NoteImage(contentType = resolver.getType(uri) ?: "application/octet-stream", bytes = bytes)
        } catch (e: Exception) {
            Log.w(LOGTAG, "Could not read image $ref from MyNotes", e)
            null
        }
    }

    /** URI of a note, also the `ACTION_VIEW` target that opens it in the MyNotes app. */
    fun noteUri(slug: String): Uri =
        BASE_URI.buildUpon().appendPath(PATH_NOTES).appendPath(slug).build()

    /** URI of a tag; only ever used as an `ACTION_VIEW` target. */
    fun tagUri(slug: String): Uri =
        BASE_URI.buildUpon().appendPath(PATH_TAGS).appendPath(slug).build()

    /**
     * An intent that opens [uri] in the MyNotes app. Restricted to that package so a note never
     * opens somewhere else, and typed explicitly because MyNotes matches these by MIME type.
     */
    fun viewIntent(context: Context, uri: Uri): Intent =
        Intent(Intent.ACTION_VIEW)
            .setPackage(PACKAGE)
            .setDataAndType(uri, context.contentResolver.getType(uri))

    /** The provider URI for an image reference in note content, or null if it is not one. */
    private fun imageUri(ref: String): Uri? {
        ARTIFACT_REF.find(ref)?.let {
            return BASE_URI.buildUpon().appendPath(PATH_ARTIFACTS).appendPath(it.groupValues[1]).build()
        }
        LOCAL_ARTIFACT_REF.find(ref)?.let {
            return BASE_URI.buildUpon().appendPath(PATH_LOCAL_ARTIFACTS).appendPath(it.groupValues[1]).build()
        }
        return null
    }

    /**
     * Runs [read] over the cursor for [uri], or returns null when MyNotes is unavailable. A missing
     * provider or a denied permission is expected (MyNotes not installed, or signed with another
     * key) and is logged rather than thrown: the caller's job is to hide the integration, not to
     * fail the screen.
     */
    private fun <T> query(context: Context, uri: Uri, projection: Array<String>, read: (android.database.Cursor) -> T?): T? =
        try {
            context.contentResolver.query(uri, projection, null, null, null)?.use(read)
        } catch (e: SecurityException) {
            Log.w(LOGTAG, "MyNotes denied access — are both apps signed with the same key?", e)
            null
        } catch (e: Exception) {
            Log.w(LOGTAG, "Could not read $uri from MyNotes", e)
            null
        }
}

/** Reference to an image uploaded to the MyNotes server, as it appears in note content. */
internal val ARTIFACT_REF = Regex("^(?:.*/)?api/v1/artifacts/([0-9a-f]{64})$")

/** Reference to an image attached on this device and not yet uploaded. */
internal val LOCAL_ARTIFACT_REF = Regex("^local-artifact://([\\w-]+)$")

data class NoteSummary(val slug: String, val title: String)

data class Note(
    val slug: String,
    val title: String,
    /** Verbatim Markdown, rendered through the MyNotes render kit — never parsed here. */
    val content: String,
    val hasFullContent: Boolean,
)

/** An image embedded in a rendered note, read from MyNotes. */
class NoteImage(val contentType: String, val bytes: ByteArray)
