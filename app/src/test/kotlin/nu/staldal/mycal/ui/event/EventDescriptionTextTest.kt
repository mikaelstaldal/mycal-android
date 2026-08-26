package nu.staldal.mycal.ui.event

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Covers the two pure pieces of the description renderer: the rule that decides which of the
 * description's anchors and the URLs detected in its text actually become links, and the scheme
 * allow-list that says which of those the app will open.
 *
 * The HTML parse and the URL detection themselves are the platform's (`Html.fromHtml`,
 * `LinkifyCompat`) and need a device, so they are not tested here.
 */
class EventDescriptionTextTest {

    // --- merging anchors with detected URLs ----------------------------------

    @Test
    fun `an anchor and a detected URL over the same text yield one link`() {
        val anchor = LinkSpan(0, 18, "https://example.com/")
        val detected = LinkSpan(0, 18, "https://example.com")
        assertEquals(listOf(anchor), mergeLinkSpans(listOf(anchor), listOf(detected)))
    }

    @Test
    fun `the anchor wins where the two overlap only partly`() {
        val anchor = LinkSpan(4, 20, "https://example.com/a")
        val detected = LinkSpan(12, 31, "https://example.com/b")
        assertEquals(listOf(anchor), mergeLinkSpans(listOf(anchor), listOf(detected)))
    }

    @Test
    fun `a detected URL outside every anchor is kept`() {
        val anchor = LinkSpan(0, 10, "https://example.com/a")
        val detected = LinkSpan(20, 40, "https://example.com/b")
        assertEquals(
            listOf(anchor, detected),
            mergeLinkSpans(listOf(anchor), listOf(detected)),
        )
    }

    @Test
    fun `links come back in text order`() {
        val early = LinkSpan(0, 5, "https://a.example/")
        val late = LinkSpan(30, 40, "https://b.example/")
        val middle = LinkSpan(10, 20, "mailto:someone@example.com")
        assertEquals(
            listOf(early, middle, late),
            mergeLinkSpans(listOf(late, early), listOf(middle)),
        )
    }

    @Test
    fun `overlapping anchors are reduced to one`() {
        val outer = LinkSpan(0, 20, "https://example.com/outer")
        val inner = LinkSpan(5, 10, "https://example.com/inner")
        assertEquals(listOf(outer), mergeLinkSpans(listOf(outer, inner), emptyList()))
    }

    @Test
    fun `an empty span is dropped`() {
        assertEquals(
            emptyList<LinkSpan>(),
            mergeLinkSpans(listOf(LinkSpan(7, 7, "https://example.com/")), emptyList()),
        )
    }

    @Test
    fun `an anchor the app will not open leaves the URL under it linkable`() {
        // `<a href="javascript:void(0)">https://example.com</a>`: the anchor is dropped by the
        // scheme allow-list before the merge, so it cannot shadow the URL the user can see.
        val unopenable = LinkSpan(0, 19, "javascript:void(0)")
        val detected = LinkSpan(0, 19, "https://example.com")
        val anchors = listOf(unopenable).filter { descriptionLinkAction(it.url.substringBefore(':')) != null }
        assertEquals(listOf(detected), mergeLinkSpans(anchors, listOf(detected)))
    }

    @Test
    fun `a description with no links yields none`() {
        assertEquals(emptyList<LinkSpan>(), mergeLinkSpans(emptyList(), emptyList()))
    }

    // --- which schemes may be opened -----------------------------------------

    @Test
    fun `web links are viewed`() {
        assertEquals(Intent.ACTION_VIEW, descriptionLinkAction("http"))
        assertEquals(Intent.ACTION_VIEW, descriptionLinkAction("https"))
    }

    @Test
    fun `mail links are sent to`() {
        assertEquals(Intent.ACTION_SENDTO, descriptionLinkAction("mailto"))
    }

    @Test
    fun `the scheme is matched case-insensitively`() {
        assertEquals(Intent.ACTION_VIEW, descriptionLinkAction("HTTPS"))
    }

    @Test
    fun `any other scheme is refused`() {
        assertNull(descriptionLinkAction("javascript"))
        assertNull(descriptionLinkAction("file"))
        assertNull(descriptionLinkAction("intent"))
        assertNull(descriptionLinkAction("content"))
    }

    @Test
    fun `a relative href has no scheme and is refused`() {
        assertNull(descriptionLinkAction(null))
    }
}
