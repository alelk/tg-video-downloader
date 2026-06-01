# yt-dlp Cheatsheet for AI Agents

> **Purpose**: Compact reference for AI agents working on yt-dlp integration in this project.
> For the full documentation see https://github.com/yt-dlp/yt-dlp

---

## Format Selection

### `-f` / `--format`

The most important option. Selects which video/audio stream to download.

```bash
# Best video + best audio, merged
yt-dlp -f "bestvideo+bestaudio" <url>

# Best video up to 1080p + best audio
yt-dlp -f "bestvideo[height<=1080]+bestaudio/best" <url>

# Specific format ID (get IDs via yt-dlp -F <url>)
yt-dlp -f "303+251" <url>

# Single best combined format (no muxing needed)
yt-dlp -f "best" <url>
```

### `-S` / `--format-sort`

Prioritizes formats by criteria. Applied AFTER `-f` filtering.

```bash
# Prefer highest resolution, then bitrate, then fps
yt-dlp -S "res,tbr,fps" <url>

# Prefer resolution up to 1080p
yt-dlp -S "res:1080,tbr,fps" <url>

# Prefer vp9 codec
yt-dlp -S "vcodec:vp9,res,tbr" <url>
```

### `--check-formats`

Verifies that selected formats are actually downloadable. Useful but can fail on some sites or slow connections — disable with `--no-check-formats` if downloads get stuck.

### Diagnosing format issues

```bash
# List all available formats for a URL
yt-dlp -F <url>

# Simulate download (no actual download)
yt-dlp --simulate -f "bestvideo+bestaudio" <url>
```

---

## Cookies

Required for age-restricted, members-only, or logged-in content.

### `--cookies-from-browser`

Reads cookies directly from an installed browser. Only works when yt-dlp runs on the same machine as the browser.

```bash
yt-dlp --cookies-from-browser chrome <url>
yt-dlp --cookies-from-browser firefox <url>
yt-dlp --cookies-from-browser safari <url>
yt-dlp --cookies-from-browser brave <url>
```

### `--cookies`

Uses a Netscape-format cookies.txt file.

```bash
yt-dlp --cookies /path/to/cookies.txt <url>
```

**Netscape format** (first line must be `# Netscape HTTP Cookie File`):
```
# Netscape HTTP Cookie File
.youtube.com	TRUE	/	TRUE	1700000000	CONSENT	YES+
.youtube.com	TRUE	/	FALSE	1700000000	LOGIN_INFO	<value>
```

### How to export cookies from browser

1. Install "Get cookies.txt LOCALLY" extension:
   - Chrome: https://chrome.google.com/webstore/detail/get-cookiestxt-locally/cclelndahbckbenkjhflpdbgdldlbecc
   - Firefox: https://addons.mozilla.org/en-US/firefox/addon/cookies-txt/
2. Open the target site and log in
3. Click the extension icon → Export → Netscape format
4. Use the file with `--cookies`

---

## Quality & Container

### `--merge-output-format`

When video and audio are downloaded separately and merged via ffmpeg:

```bash
yt-dlp --merge-output-format mkv <url>   # recommended: supports all codecs
yt-dlp --merge-output-format mp4 <url>   # widely compatible
yt-dlp --merge-output-format webm <url>
```

---

## Network & Rate Limiting

```bash
# Limit download speed (avoid bans)
yt-dlp --rate-limit 5M <url>
yt-dlp --rate-limit 500K <url>

# Add random sleep between requests
yt-dlp --sleep-interval 2 --max-sleep-interval 5 <url>

# Custom User-Agent
yt-dlp --user-agent "Mozilla/5.0 ..." <url>

# Socket timeout
yt-dlp --socket-timeout 30 <url>

# Parallel fragment downloads (for DASH/HLS)
yt-dlp --concurrent-fragments 5 <url>
```

---

## Retries & Resilience

```bash
yt-dlp --retries 5 <url>
yt-dlp --fragment-retries 30 <url>
yt-dlp --extractor-retries 5 <url>
yt-dlp --retry-sleep fragment:exp=1:5:30 <url>
yt-dlp --retry-sleep http:exp=1:2:30 <url>
```

---

## SSL Workarounds

Some Russian sites (RuTube, VK) have non-standard SSL:

```bash
# RuTube: fixes UNEXPECTED_EOF_WHILE_READING
yt-dlp --legacy-server-connect <url>

# Disable certificate check entirely (unsafe)
yt-dlp --no-check-certificate <url>
```

---

## Subtitles

```bash
# Download subtitles
yt-dlp --write-subs --sub-langs ru,en <url>

# Download auto-generated subtitles (YouTube)
yt-dlp --write-auto-subs --sub-langs ru <url>

# Embed subtitles into file (requires ffmpeg)
yt-dlp --write-subs --embed-subs <url>
```

---

## Extractor Args (Site-specific)

Pass extra parameters to specific extractors:

```bash
# YouTube: use web client (bypass some restrictions)
yt-dlp --extractor-args "youtube:player_client=web" <url>

# YouTube: use iOS client (different format availability)
yt-dlp --extractor-args "youtube:player_client=ios" <url>

# VK: skip certificate check for VK specifically
yt-dlp --extractor-args "vk:nocheckcertificate=1" <url>

# Multiple extractors
yt-dlp --extractor-args "youtube:player_client=web;vk:nocheckcertificate=1" <url>
```

---

## SponsorBlock (YouTube)

Automatically cut sponsored segments and other annoyances:

```bash
# Remove sponsor segments
yt-dlp --sponsorblock-remove sponsor <url>

# Remove multiple categories
yt-dlp --sponsorblock-remove sponsor,selfpromo,interaction <url>

# Available categories: sponsor, intro, outro, selfpromo, preview,
#   filler, poi_highlight, chapter, interaction, music_offtopic
```

---

## Proxy

```bash
# HTTP proxy
yt-dlp --proxy http://127.0.0.1:8080 <url>

# SOCKS5 proxy
yt-dlp --proxy socks5://127.0.0.1:1080 <url>

# With auth
yt-dlp --proxy socks5://user:pass@127.0.0.1:1080 <url>
```

---

## Common Troubleshooting

| Problem                                                           | Solution                                                                                                                                                               |
|-------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **"No supported JavaScript runtime"**                             | Set **YouTube Player Client** = `ios` or `android` (Settings → Advanced). No deno needed. Or install deno: `curl -fsSL https://deno.land/install.sh \| sh`            |
| **"SABR-only streaming experiment"** (android_vr formats missing) | Use `ios` or `web` player client (Settings → Advanced → YouTube Player Client).                                                                                       |
| Only 360p downloaded                                              | Check `yt-dlp -F <url>` — are higher formats available? If yes, try `-f bestvideo+bestaudio` without `--check-formats`                                                |
| Age-restricted content                                            | Set `--cookies` or `--cookies-from-browser`                                                                                                                           |
| SSL errors on RuTube                                              | Add `--legacy-server-connect`                                                                                                                                         |
| Download stuck on format check                                    | Disable with `--no-check-formats` (uncheck "Check formats" in Settings)                                                                                               |
| Rate limited / 429                                                | Add `--sleep-interval 2 --max-sleep-interval 8 --rate-limit 2M`                                                                                                       |
| YouTube "Sign in to confirm age"                                  | Export cookies from browser, use `--cookies`                                                                                                                          |
| "This video is only available to Music Premium members"           | Need YouTube Music cookies                                                                                                                                            |
| VK video not downloading                                          | Try `--extractor-args "vk:nocheckcertificate=1"` (Settings → Advanced → Extractor args)                                                                               |

### YouTube player_client reference

| Client        | JS runtime needed? | Notes                                                               |
|---------------|--------------------|---------------------------------------------------------------------|
| `ios`         | ❌ No               | Best for most cases. High quality, no JS needed. **Auto-fallback.** |
| `web`         | ✅ Yes              | Default. Some formats only available here.                          |
| `android`     | ❌ No               | Alternative to ios.                                                 |
| `mweb`        | ❌ No               | Mobile web, lower quality.                                          |
| `tv_embedded` | ❌ No               | For embedded players.                                               |

---

## Project-specific: How settings map to yt-dlp args

| `YtDlpConfig` field       | yt-dlp argument                              |
|---------------------------|----------------------------------------------|
| `cookiesFromBrowser`      | `--cookies-from-browser <value>`             |
| `cookiesContent`          | Written to temp file → `--cookies <tmpfile>` |
| `cookiesFile`             | `--cookies <value>`                          |
| `legacyServerConnect`     | `--legacy-server-connect`                    |
| `noCheckCertificate`      | `--no-check-certificate`                     |
| `preferredFormats`        | `-f <value>` (overrides auto-selection)      |
| `formatSort`              | `-S <value>`                                 |
| `checkFormats`            | `--check-formats` (when `true`)              |
| `mergeOutputFormat`       | `--merge-output-format <value>`              |
| `rateLimit`               | `--rate-limit <value>`                       |
| `sleepInterval`           | `--sleep-interval <value>`                   |
| `maxSleepInterval`        | `--max-sleep-interval <value>`               |
| `writeSubs`               | `--write-subs`                               |
| `writeAutoSubs`           | `--write-auto-subs`                          |
| `subLangs`                | `--sub-langs <value>`                        |
| `embedSubs`               | `--embed-subs`                               |
| `concurrentFragments`     | `--concurrent-fragments <value>`             |
| `socketTimeout`           | `--socket-timeout <value>`                   |
| `extractorArgs`           | `--extractor-args <value>`                   |
| `sponsorBlockRemove`      | `--sponsorblock-remove <value>`              |
| `userAgent`               | `--user-agent <value>`                       |
| `extractorOverrides[key]` | Per-URL overrides for SSL/proxy              |


