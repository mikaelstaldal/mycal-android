#!/usr/bin/env bash
#
# Refreshes app/src/main/assets/renderer/ — the vendored MyNotes render kit that
# EventDetailScreen loads in a WebView to display the note linked to an event.
#
# The kit *is* the MyNotes web client's Markdown pipeline (markdown-it →
# DOMPurify, plus Mermaid, AsciiMath, inline Lucide icons, emoji shortcodes,
# callouts, wikilinks) and its stylesheet, packaged as a static page exposing
# globalThis.MyNotesRender. Vendoring it verbatim is what lets MyCal show a note
# exactly as MyNotes does without a second implementation of that dialect —
# there is nothing here to keep in sync by hand. The MyCal web frontend does the
# same thing with an iframe against /mynotes/render/ (see web/ts/components/
# NotePanel.tsx in the mycal repo).
#
# The mynotes repo owns the file list, so this just delegates to its
# tools/dist-renderer.sh. That script is a plain copy of build output — run
# ./build.sh in the mynotes repo first.
#
# Usage: tools/sync-renderer.sh [path-to-mynotes-repo]
#   Defaults to ../mynotes relative to this repo's root.
#
# Commit the result: the kit ships in the APK, and a note renders without
# fetching anything but its own images.
set -euo pipefail

repo_root="$(cd "$(dirname "$0")/.." && pwd)"
mynotes="${1:-$repo_root/../mynotes}"
dist="$mynotes/tools/dist-renderer.sh"
dest="$repo_root/app/src/main/assets/renderer"

if [[ ! -x "$dist" ]]; then
    echo "error: $dist not found or not executable (pass the mynotes repo path as \$1)" >&2
    exit 1
fi

"$dist" "$dest"

echo "Vendored the render kit into $dest — commit the result."
