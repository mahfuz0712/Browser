# Browser

Sci-fi HUD styled web browser for Android 5.0 (API 21) and newer, built on the system WebView (Chromium).
Same design language and architecture as the Dialer / Calculator apps (Jetpack Compose, cyan/amber HUD, phone + tablet layouts).

Package `com.mux.android.browser` - Version `1.0.0`

## Features
- **Tabs**: multi-tab, tab switcher, tab strip on tablets, restore tabs on start, pop-ups open as child tabs
- **Private tabs**: no history/cache, separate cookie jar when the device WebView supports profiles
- **Omnibox**: URL/search detection, suggestions from bookmarks, history and the search engine (Google, DuckDuckGo, Bing, Brave, Ecosia, Startpage, Yahoo)
- **Start page / speed dial** (Opera style) from bookmarks and most visited sites
- **Blocking**: built-in ad & tracker host list + cosmetic hiding (per-site switch), pop-up blocker, HTTPS-only mode, Safe Browsing, third-party cookie blocking, DNT / GPC headers
- **Reader mode**, **dark pages**, **desktop site**, page text size, data saver (no images), find in page, pull to refresh, toolbars hide on scroll
- **Downloads** (Android DownloadManager) with in-app list; **bookmarks**; **history** grouped by day
- Camera / microphone / location prompts, file upload, fullscreen video, JS dialogs, SSL warning page, HTTP auth
- Link & image long-press menu, share, copy, print / save as PDF, add to home screen, translate
- Can be set as **default browser**; handles links and "Share > Browser"
- Clear browsing data (history, cookies, cache, downloads), clear on exit

## Build
Open in Android Studio, or `gradle assembleDebug` (Gradle 8.9, JDK 17). CI: `.github/workflows/build.yml`.
Updates are read from GitHub releases of `mahfuz0712/Browser` (edit `AppInfo.kt`).
