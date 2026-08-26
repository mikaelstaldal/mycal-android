package nu.staldal.mycal.ui.event

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.text.Html
import android.text.SpannableString
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.URLSpan
import android.text.style.UnderlineSpan
import android.text.util.Linkify
import android.widget.Toast
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.core.net.toUri
import androidx.core.text.util.LinkifyCompat

/** One link in the rendered description: the text it covers, and where it goes. */
internal data class LinkSpan(val start: Int, val end: Int, val url: String)

/**
 * The event description, as the detail screen shows it: read-only, selectable, with live links.
 *
 * The description is rich text — the web frontend edits it with a WYSIWYG editor and stores HTML —
 * so it arrives as an HTML fragment and is converted to an [AnnotatedString] rather than shown raw.
 * Two things the plain [Text] it replaced could not do:
 *
 *  - **Selection.** The text sits in a [SelectionContainer], so it can be selected and copied
 *    without going through the edit form.
 *  - **Links.** URLs become real links, opened through [openDescriptionLink].
 *
 * Those two do not fight: links are Compose's own [LinkAnnotation.Url] annotations, which a
 * [SelectionContainer] understands — a tap follows the link, a drag selects across it. (The older
 * `ClickableText`/tap-detector approach would have swallowed the gestures selection needs.)
 */
@Composable
fun EventDescriptionText(html: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val linkStyles = TextLinkStyles(
        style = SpanStyle(
            color = MaterialTheme.colorScheme.primary,
            textDecoration = TextDecoration.Underline,
        ),
    )
    val text = remember(html, linkStyles) {
        descriptionToAnnotatedString(html, linkStyles) { url -> openDescriptionLink(context, url) }
    }
    SelectionContainer(modifier = modifier) {
        Text(text)
    }
}

/**
 * Converts a description's HTML into styled, linked text.
 *
 * Links come from two places, because descriptions do. One written in the web frontend carries
 * real `<a>` anchors, whose text need not be the URL; one typed into this app's plain description
 * field carries none, and a URL in it is just characters. So anchors are taken from the parsed
 * HTML, bare URLs and e-mail addresses are detected with [LinkifyCompat] over the plain text, and
 * [mergeLinkSpans] keeps the anchors where the two disagree.
 *
 * Only the schemes [descriptionLinkAction] can open become links; anything else stays plain text
 * rather than rendering as a link that does nothing when tapped. That filter runs *before* the
 * merge, so an anchor pointing somewhere unopenable does not shadow the plain URL under it — the
 * text still links to what it visibly says.
 */
internal fun descriptionToAnnotatedString(
    html: String,
    linkStyles: TextLinkStyles,
    onLinkClick: (String) -> Unit,
): AnnotatedString {
    val spanned = Html.fromHtml(html, Html.FROM_HTML_MODE_COMPACT)
    val plain = spanned.toString()
    return buildAnnotatedString {
        append(plain)
        spanned.getSpans(0, spanned.length, Any::class.java).forEach { span ->
            val start = spanned.getSpanStart(span)
            val end = spanned.getSpanEnd(span)
            when (span) {
                is StyleSpan -> when (span.style) {
                    Typeface.BOLD -> addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, end)
                    Typeface.ITALIC -> addStyle(SpanStyle(fontStyle = FontStyle.Italic), start, end)
                    Typeface.BOLD_ITALIC -> addStyle(SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic), start, end)
                }
                is UnderlineSpan -> addStyle(SpanStyle(textDecoration = TextDecoration.Underline), start, end)
                is StrikethroughSpan -> addStyle(SpanStyle(textDecoration = TextDecoration.LineThrough), start, end)
            }
        }
        val anchors = spanned.getSpans(0, spanned.length, URLSpan::class.java).map {
            LinkSpan(spanned.getSpanStart(it), spanned.getSpanEnd(it), it.url)
        }
        mergeLinkSpans(anchors.filter(::isOpenable), detectLinks(plain).filter(::isOpenable))
            .forEach { link ->
                addLink(
                    LinkAnnotation.Url(link.url, linkStyles) { onLinkClick(link.url) },
                    link.start,
                    link.end,
                )
            }
    }
}

/** Whether this link's scheme is one the app is willing to hand to another app. */
private fun isOpenable(link: LinkSpan): Boolean =
    descriptionLinkAction(link.url.toUri().scheme) != null

/**
 * The bare URLs and e-mail addresses in [text], as the platform recognises them.
 *
 * Linkify is run over a copy holding no spans of its own: `LinkifyCompat.addLinks` starts by
 * *removing* every existing [URLSpan], so running it on the parsed description directly would
 * throw away the anchors it was meant to complement.
 */
private fun detectLinks(text: String): List<LinkSpan> {
    val spannable = SpannableString(text)
    LinkifyCompat.addLinks(spannable, Linkify.WEB_URLS or Linkify.EMAIL_ADDRESSES)
    return spannable.getSpans(0, spannable.length, URLSpan::class.java).map {
        LinkSpan(spannable.getSpanStart(it), spannable.getSpanEnd(it), it.url)
    }
}

/**
 * The links to annotate, given the description's own anchors and what was detected in its text.
 *
 * Compose links may not overlap, and the two sources routinely do — an anchor whose text *is* its
 * URL is found twice. Anchors are considered first and so win: their href is what the author
 * wrote, while the detected span only ever repeats the visible text.
 */
internal fun mergeLinkSpans(anchors: List<LinkSpan>, detected: List<LinkSpan>): List<LinkSpan> {
    val accepted = mutableListOf<LinkSpan>()
    for (link in anchors.sortedBy { it.start } + detected.sortedBy { it.start }) {
        if (link.start >= link.end) continue
        if (accepted.none { it.start < link.end && link.start < it.end }) accepted.add(link)
    }
    return accepted.sortedBy { it.start }
}

/**
 * The intent action that opens a link with this scheme, or null if the app will not open it.
 *
 * The same allow-list the rendered-note WebView applies (see `NoteRendererWebView`): a description
 * is content off the server, so it does not get to aim an arbitrary scheme at the device.
 */
internal fun descriptionLinkAction(scheme: String?): String? = when (scheme?.lowercase()) {
    "http", "https" -> Intent.ACTION_VIEW
    "mailto" -> Intent.ACTION_SENDTO
    else -> null
}

/** Hands a tapped description link to whichever app handles it. */
private fun openDescriptionLink(context: Context, url: String) {
    val uri = url.toUri()
    val action = descriptionLinkAction(uri.scheme) ?: return
    try {
        context.startActivity(Intent(action, uri))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "No app found to open this link: $uri", Toast.LENGTH_SHORT).show()
    }
}
