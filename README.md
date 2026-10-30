# OperaFast

A small, fast Android browser with an Opera-like dark interface and nothing extra.
Native Java on the system WebView, no third-party libraries (APK is a few MB).

## What it has

- Address bar on top (domain shown, full URL when editing), red progress line, ad-block shield.
- Bottom bar like Opera: back, forward, new tab, tab counter, menu.
- Tabs: grid switcher, close, popups/`target=_blank` open as tabs, Back closes a link tab and
  returns to the page that opened it. Tabs are restored after a restart; hidden tabs release
  their WebView when memory is tight.
- History grouped by day (tap to open, remove one entry or clear all).
- Downloads via the system DownloadManager: progress list, open, cancel/delete, notifications.
- Start page with quick links (native, no WebView needed).
- Ad blocking by host list (`app/src/main/assets/adblock/hosts.txt`), per-site "allow".
- Menu: new tab, history, downloads, share page, desktop site, clear browsing data.

## Fast start / use from a launcher

- No Python/Kivy runtime, UI is built in code, WebView is created right after the first frame.
- Back on the first page moves the app to background instead of closing it (warm next start).
- Registered for `http`/`https` links, `ACTION_WEB_SEARCH` and "Share text", so it can also be
  chosen as the default browser.

From DynamicAI (Kotlin):

```kotlin
const val BROWSER = "org.blackorange.operafast"

fun openInBrowser(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        .setPackage(BROWSER)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try { context.startActivity(intent) } catch (e: ActivityNotFoundException) { /* not installed */ }
}

fun searchInBrowser(context: Context, query: String) {
    val intent = Intent(Intent.ACTION_WEB_SEARCH)
        .putExtra(SearchManager.QUERY, query)
        .setPackage(BROWSER)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try { context.startActivity(intent) } catch (e: ActivityNotFoundException) { }
}
```

Plain text from the search box is turned into a URL or a DuckDuckGo search by the browser itself.

## Build

CI (`.github/workflows/build.yml`) builds a signed release APK on every push
(artifact `operafast-release`). Locally: `gradle assembleRelease` (JDK 17, Android SDK 34).
The APK is signed with the committed `android/debug.keystore`, so new builds update old ones.

## Update: Opera-style tabs & polish
- Tab switcher: swipeable card carousel with page previews, thumbnail strip, floating action bar
- Bottom-sheet menu with toggles: ad blocker, night mode, desktop site, no images
- Bookmarks, find in page, long-press on a link (new tab / copy / share)
- Smooth animations: overlays, menu, tab switch, progress bar

## Update: passwords, private tabs, Ukrainian, settings
- Save / auto-fill logins (AES-GCM, key in Android Keystore), manage them in Menu -> Passwords
- Private tabs (separate "Private" section in the tab switcher): no history, no saved logins, not restored; cookies of visited sites are removed when the last private tab closes. Screenshots are blocked while one is open.
- Ukrainian UI (Settings -> Language, default follows the system)
- Settings: language, search engine, start page (speed dial or a custom address), editable speed dial (long-press a tile, "+" to add)
- Address-bar suggestions: typed text, bookmarks, history and search-engine suggestions (toggle in Settings -> Search)

## For launchers (DynamicAI)
- Open a search: `Intent(ACTION_WEB_SEARCH).setPackage("org.blackorange.operafast").putExtra("query", q)`; open a page: `ACTION_VIEW` + https URI with the same package.
- Search bookmarks/history: `content://org.blackorange.operafast.search/pages?q=text&limit=6` (columns `kind`, `title`, `url`). Needs the runtime permission `org.blackorange.operafast.permission.READ_PAGES` (the user is asked once).
