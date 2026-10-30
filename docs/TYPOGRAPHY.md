# Typography and language

The reference screenshots suggest a Traditional Chinese Song/Ming serif and a Latin typewriter monospace. The exact original families cannot be identified from screenshots alone.

Bundled alternatives:
- Noto Serif TC, Google Fonts / Noto: https://github.com/google/fonts/tree/main/ofl/notoseriftc
- IBM Plex Mono Regular, IBM / Google Fonts: https://github.com/google/fonts/tree/main/ofl/ibmplexmono

Noto Serif SC supplies missing Simplified Chinese glyphs for existing records: https://github.com/google/fonts/tree/main/ofl/notoserifsc

All three use SIL Open Font License 1.1. Original notices and complete licenses are bundled in `app/src/main/assets/fonts/`.

`res/font/memory_regular.ttf` is a renamed derivative, **Memory Typography Regular**. Noto Serif TC's variable font was instantiated at weight 400. Latin U+0000–024F was removed from that copy and supplied by IBM Plex Mono. Missing glyphs from Noto Serif SC (weight 400) were then added without replacing existing glyph mappings. FontTools merged the glyphs, dropping BASE, DSIG, vhea, vmtx and VORG tables. All name identifiers for the family/style were renamed; the original families remain credited here and in the bundled licenses. This gives consistent Chinese/Latin typography in native widgets, dialogs and Canvas on Android 24+, without downloading fonts at runtime. Bold and italic use Android's style synthesis. User-imported custom fonts remain selectable.

UI text is stored in `values/ui_strings.xml` (Traditional Chinese, default) and `values-en/ui_strings.xml` (English). L10n reads the selected locale for programmatically built UI, and AppCompat applies it to XML and platform widgets. Style-name arrays are evaluated on demand after a language change. The choice is persisted under `appearance/language` (default `zh-TW`). Change language from the Explore sheet (語言 / Language).

Existing user records, excerpts, lyrics, letters, transcripts and chat replies are not translated or rewritten. Internal Agent prompts and retrieval remain in their original implementation language; switching the interface does not translate a model's response or imported content.
