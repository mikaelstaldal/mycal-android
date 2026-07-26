package nu.staldal.mycal.ui.note

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import nu.staldal.mycal.data.api.MyNotesClient
import nu.staldal.mycal.data.api.NoteImage
import org.json.JSONObject
import java.io.ByteArrayInputStream

/**
 * Displays the MyNotes note linked to an event by driving the vendored MyNotes render kit in a
 * WebView.
 *
 * The kit (app/src/main/assets/renderer/, refreshed by tools/sync-renderer.sh) is the MyNotes web
 * client's own Markdown pipeline — markdown-it → DOMPurify, plus Mermaid, AsciiMath, inline Lucide
 * icons, emoji shortcodes, callouts and wikilinks — packaged as a static page. A note therefore
 * renders here exactly as it does in MyNotes, and MyCal never parses Markdown itself. The MyCal web
 * frontend embeds the very same kit in an iframe (see NotePanel.tsx in the mycal repo); this is the
 * native equivalent, and the approach the MyNotes Android app uses for its own notes.
 *
 * Data flows one way in and one way out:
 *  - **in**: Markdown and the theme are pushed through the page's JS API with
 *    [WebView.evaluateJavascript]. Arguments are JSON-quoted, so note content is never spliced into
 *    HTML or into JS syntax; the kit's DOMPurify gate remains the only path from note content to
 *    the DOM.
 *  - **out**: taps surface through [WebViewClient.shouldOverrideUrlLoading] (see [NoteWebViewClient]).
 *
 * Everything the page loads is served from app assets or read from the MyNotes app on this device —
 * see [NoteWebViewClient.shouldInterceptRequest], which is an allow-list. Showing a note therefore
 * makes no network request at all, and works with no connectivity whenever MyNotes has the note.
 */

/** Origin [WebViewAssetLoader] serves app assets from; the WebView's real origin for this page. */
private const val ASSET_HOST = "appassets.androidplatform.net"

private const val ASSET_ORIGIN = "https://$ASSET_HOST"

/** The render kit's host page. Its relative imports resolve under /assets/renderer/. */
private const val RENDERER_URL = "$ASSET_ORIGIN/assets/renderer/render/index.html"

/** Name of the injected object the page posts its content height through. */
private const val HEIGHT_CHANNEL = "myCalNoteHeight"

/** JS expression for the note's rendered height, in CSS pixels (1 CSS px = 1 dp here). */
private const val HEIGHT_EXPRESSION = "Math.ceil(document.documentElement.getBoundingClientRect().height)"

/** Height used until the page reports its own, and whenever it cannot. */
private val FALLBACK_HEIGHT = 320.dp

/** Delays at which the height is re-read when the page cannot push it (see [renderScript]). */
private val HEIGHT_POLL_DELAYS_MS = longArrayOf(200, 800, 2000)

/**
 * Root-relative path the note Markdown's `local-artifact://<id>` references are rewritten to before
 * rendering. The renderer's URL allow-list (DOMPurify) drops unknown schemes but keeps relative
 * URLs, so an image MyNotes has attached but not yet uploaded has to travel as a path and be
 * resolved back in [NoteWebViewClient.shouldInterceptRequest].
 */
private const val LOCAL_ARTIFACT_PATH = "/local-artifact/"

private val LOCAL_ARTIFACT_PATH_REF = Regex("^$LOCAL_ARTIFACT_PATH([\\w-]+)$")
private val ARTIFACT_PATH_REF = Regex("^(?:.*/)?api/v1/artifacts/([0-9a-f]{64})$")

/** Served for any request the allow-list rejects, so nothing silently reaches the network. */
private fun blockedResponse() =
    WebResourceResponse(null, null, 403, "Blocked", emptyMap(), ByteArrayInputStream(ByteArray(0)))

/** Fetches an image a rendered note references from the MyNotes app. Called on a background thread. */
fun interface NoteImageFetcher {
    /** @param ref the reference as it appears in note content, e.g. `/api/v1/artifacts/<sha>`. */
    fun fetch(ref: String): NoteImage?
}

/**
 * Rewrites `local-artifact://<id>` image references to [LOCAL_ARTIFACT_PATH] so they survive the
 * renderer's sanitization (see [LOCAL_ARTIFACT_PATH]). Applied to the Markdown, exactly as the
 * MyNotes app does before rendering the same content.
 */
internal fun rewriteLocalArtifactRefs(markdown: String): String =
    markdown.replace("local-artifact://", LOCAL_ARTIFACT_PATH)

/**
 * The image reference to ask the MyNotes app for, or null when the request is not one this app will
 * make on the note's behalf — in which case it is blocked.
 *
 * Two kinds are allowed, both resolvable from MyNotes' own artifact cache:
 *  - `api/v1/artifacts/<sha256>` — an image uploaded to the MyNotes server. Matching is on the path
 *    alone, so it resolves whether the note stores the reference root-relative or absolute (MyNotes
 *    rewrites uploads to an absolute URL against its own base). Either way the bytes come from the
 *    MyNotes app, never from a host named in the note.
 *  - `/local-artifact/<id>` — an image attached on this device and not yet uploaded, rewritten by
 *    [rewriteLocalArtifactRefs].
 *
 * Icon requests (`/api/v1/icons/…`) are deliberately absent: the renderer draws every icon it knows
 * inline as `<svg>`, so a request only escapes for a name the vendored kit lacks — those render
 * broken until tools/sync-renderer.sh picks up a newer kit, as they do in the MyNotes app itself.
 *
 * Everything else — a third-party image, a tracking pixel, any other endpoint — has no entry here
 * and is refused, so displaying a note cannot phone home.
 */
internal fun noteImageRefFor(path: String): String? {
    LOCAL_ARTIFACT_PATH_REF.find(path)?.let { return "local-artifact://${it.groupValues[1]}" }
    ARTIFACT_PATH_REF.find(path)?.let { return "/api/v1/artifacts/${it.groupValues[1]}" }
    return null
}

@Composable
fun NoteRendererWebView(
    markdown: String,
    dark: Boolean,
    background: Color,
    onBackground: Color,
    linkColor: Color,
    imageFetcher: NoteImageFetcher,
    modifier: Modifier = Modifier,
) {
    // The note is one section of a scrolling detail screen rather than a pane of its own, so the
    // view is sized to its content: the page reports its height (and keeps reporting it as images
    // and diagrams settle) and the whole screen scrolls as one.
    var contentHeight by remember { mutableStateOf(FALLBACK_HEIGHT) }
    val script = renderScript(markdown, dark, background, onBackground, linkColor)

    AndroidView(
        modifier = modifier.height(contentHeight),
        factory = { ctx ->
            WebView(ctx).apply {
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                // The renderer is JavaScript. The page's own Content-Security-Policy plus the
                // request allow-list below keep it to app assets and MyNotes' own images.
                settings.javaScriptEnabled = true
                // Keep target="_blank" links (the renderer marks external links so) coming through
                // shouldOverrideUrlLoading rather than trying to open a second window.
                settings.setSupportMultipleWindows(false)
                setBackgroundColor(background.toArgb())
                isVerticalScrollBarEnabled = false
                overScrollMode = View.OVER_SCROLL_NEVER
                if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
                    // A ResizeObserver in the page (installed by renderScript) posts the content
                    // height here; scoped to the asset origin, and the only inbound channel.
                    WebViewCompat.addWebMessageListener(this, HEIGHT_CHANNEL, setOf(ASSET_ORIGIN)) { _, message, _, _, _ ->
                        message.data?.toFloatOrNull()?.let { height ->
                            if (height > 0) contentHeight = height.dp
                        }
                    }
                }
                webViewClient = NoteWebViewClient(ctx.applicationContext, imageFetcher) { height ->
                    if (height > 0.dp) contentHeight = height
                }
                loadUrl(RENDERER_URL)
            }
        },
        update = { webView ->
            val client = webView.webViewClient as NoteWebViewClient
            client.pending = script
            // evaluateJavascript before the page has finished loading is dropped, so the first push
            // is made by onPageFinished instead.
            if (client.loaded) client.push(webView, script)
        },
        onRelease = { it.destroy() },
    )
}

/**
 * The JS pushed into the page: theme, then content, then the height reporter.
 *
 * The app's Material colours are passed as CSS custom-property overrides so the note blends into
 * the surrounding app chrome, while every other aspect of the styling (callout accents, code
 * blocks, tables) stays the canonical one from the kit's note.css. Overrides persist until
 * replaced, so all of them are sent on every call.
 *
 * The trailing snippet installs a ResizeObserver that posts the document height back through
 * [HEIGHT_CHANNEL], once, so the embedder can size the view to the note. It is a no-op when the
 * channel is unavailable (an old WebView without WEB_MESSAGE_LISTENER); [NoteWebViewClient] then
 * reads the height directly instead.
 *
 * The height measured is the root element's box, not `scrollHeight`: the latter never falls below
 * the viewport, and since the viewport here *is* the height we set, a note shorter than the current
 * view could never shrink it back.
 */
private fun renderScript(
    markdown: String,
    dark: Boolean,
    background: Color,
    onBackground: Color,
    linkColor: Color,
): String {
    val vars = JSONObject(
        mapOf(
            "--bg" to background.toCssHex(),
            "--fg" to onBackground.toCssHex(),
            "--primary" to linkColor.toCssHex(),
        )
    )
    val theme = if (dark) "dark" else "light"
    val content = JSONObject.quote(rewriteLocalArtifactRefs(markdown))
    return """
        MyNotesRender.setTheme("$theme", $vars);
        MyNotesRender.render($content);
        (function () {
          if (window.__myCalHeightReporter || typeof $HEIGHT_CHANNEL === "undefined") return;
          window.__myCalHeightReporter = true;
          var post = function () { $HEIGHT_CHANNEL.postMessage(String($HEIGHT_EXPRESSION)); };
          new ResizeObserver(post).observe(document.documentElement);
          post();
        })();
    """.trimIndent()
}

private fun Color.toCssHex(): String =
    String.format(java.util.Locale.ROOT, "#%06X", 0xFFFFFF and toArgb())

/**
 * Serves the render kit, fetches the note's images, and routes taps.
 *
 * [shouldInterceptRequest] is an **allow-list**: only the kit's own asset files and the images the
 * MyNotes app can resolve ([noteImageRefFor]) are answered; everything else is blocked.
 */
private class NoteWebViewClient(
    context: android.content.Context,
    private val imageFetcher: NoteImageFetcher,
    private val onHeight: (Dp) -> Unit,
) : WebViewClient() {

    private val assetLoader = WebViewAssetLoader.Builder()
        .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
        .build()

    /** The script to push once the page is ready; also re-pushed after a reload. */
    var pending: String? = null

    /** Whether the host page has finished loading, so evaluateJavascript will not be dropped. */
    var loaded: Boolean = false
        private set

    /** The script last pushed, so a recomposition does not re-render an unchanged note. */
    private var lastPushed: String? = null

    override fun onPageFinished(view: WebView, url: String) {
        loaded = true
        lastPushed = null // a reload emptied the page, so the same script must be pushed again
        pending?.let { push(view, it) }
    }

    /**
     * Pushes [script] into the page, unless it is already there. Where the page cannot report its
     * own height (no WEB_MESSAGE_LISTENER), the height is read back a few times instead, to catch
     * images and Mermaid diagrams that settle after the initial layout.
     */
    fun push(view: WebView, script: String) {
        if (script == lastPushed) return
        lastPushed = script
        view.evaluateJavascript(script, null)
        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) return
        val handler = Handler(Looper.getMainLooper())
        for (delay in HEIGHT_POLL_DELAYS_MS) {
            handler.postDelayed({
                view.evaluateJavascript(HEIGHT_EXPRESSION) { value ->
                    value?.trim('"')?.toFloatOrNull()?.let { onHeight(it.dp) }
                }
            }, delay)
        }
    }

    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
        val uri = request.url
        val path = uri.path ?: return blockedResponse()

        if (uri.host == ASSET_HOST && path.startsWith("/assets/")) {
            // The kit's own files (host page, compiled modules, vendored bundles, stylesheet).
            return assetLoader.shouldInterceptRequest(uri) ?: blockedResponse()
        }

        // An image the note references; anything else is blocked.
        val ref = noteImageRefFor(path) ?: return blockedResponse()
        // Runs on a background thread, so the cross-process read is safe to block on.
        val image = imageFetcher.fetch(ref) ?: return blockedResponse()
        return WebResourceResponse(
            image.contentType.substringBefore(';'),
            null,
            200,
            "OK",
            mapOf("Cache-Control" to "no-store"),
            ByteArrayInputStream(image.bytes),
        )
    }

    /**
     * Routes taps inside the rendered note. The renderer emits the same URLs as the MyNotes web UI,
     * so wikilinks arrive as root-relative `/notes/<slug>` and `/tags/<slug>` against the asset
     * origin; MyCal has no note browser of its own, so those hand the user over to the MyNotes app.
     * http(s)/mailto links open in an external app — the WebView only ever hosts the render kit.
     * Anything else is blocked.
     */
    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val context = view.context
        val uri = request.url
        val intent = if (uri.host == ASSET_HOST) {
            myNotesIntent(context, uri) ?: return true
        } else {
            when (uri.scheme?.lowercase()) {
                "http", "https" -> Intent(Intent.ACTION_VIEW, uri)
                "mailto" -> Intent(Intent.ACTION_SENDTO, uri)
                else -> return true
            }
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "No app found to open this link: ${intent.data}", Toast.LENGTH_SHORT).show()
        }
        return true
    }

    /** An intent opening a wikilink in the MyNotes app, or null for any other same-origin URL. */
    private fun myNotesIntent(context: android.content.Context, uri: Uri): Intent? {
        val segments = uri.pathSegments
        if (segments.size != 2) return null
        val target = when (segments[0]) {
            "notes" -> MyNotesClient.noteUri(segments[1])
            "tags" -> MyNotesClient.tagUri(segments[1])
            else -> return null
        }
        return MyNotesClient.viewIntent(context, target)
    }
}
