# Shipped Lyricify helper: static decompilation findings

The original BetterLyrics v1.3.527.0 x64 package contains `Lyricify.Lyrics.Helper.dll`, 830,464 bytes, SHA-256 `33543eb8d9d63c16b6137cefeb069499b98f49a27b77730222ebb0c2f1f8b57c`. REA classifies it as CIL-only AnyCPU, assembly version 0.1.4.0 targeting `.NETStandard,Version=v2.1`. REA artifact evidence: `ev_077a7b283ad41ba305d5616d96da03b81aa13a5a301d7d290d1527e3da643c31`, retained in `rea-artifact.json`.

ILSpy 11.1.0.9782 decompiled 23 selected types to neighboring `.cs` files. `decompilation-manifest.json` records their digests. No target assembly code was loaded/executed, and no live song request was made in this investigation. Static code establishes intended requests/data transformations, not current server availability.

Focused REA member/CIL inspection of QRC/KRC decrypters and parsers is retained as `rea-parser-members.json`, evidence `ev_d46da04ec6cba1f1129f9ff48c479d4a431cab565f3370711c2c3b2fd147dc79`.

## Search is two stages

`Lyricify.Lyrics.Helpers.SearchHelper.Search` forwards to provider `Searcher.SearchForResult`. `Lyricify.Lyrics.Searchers.Searcher.cs:55–101` first searches title+artist+album, strips feature suffixes for fallback title, then tries title+artist/title fallback queries when needed. It assigns discrete `CompareHelper.MatchType` and sorts before returning a candidate. BetterLyrics then applies its own Jaro–Winkler weighted percentage to the returned candidate. These are two distinct scoring systems.

`Searcher.cs:32–47` accepts minimum MatchType. BetterLyrics passes `NoMatch`, allowing helper's candidate to proceed to the application's stricter percentage threshold.

## QQ Music actual verbatim lyrics path

`Lyricify.Lyrics.Providers.Web.QQMusic.Api.Search` (`:40–68`) posts JSON to `https://u.y.qq.com/cgi-bin/musicu.fcg` with `req_1`, module `music.search.SearchCgiService`, method `DoSearchForQQMusicDesktop`, query/search_type/page_num/num_per_page.

The source application's `SearchQQAsync` calls helper `GetLyricsAsync(numeric music ID)`, not the separate ordinary `GetLyric(songMid)` method. Exact `GetLyricsAsync` behavior (`Api.cs:226–302`):

1. Form POST to `https://c.y.qq.com/qqmusic/fcgi-bin/lyric_download.fcg` with version 15, miniversion 82, lrctype 4 and musicid.
2. Removes XML comment wrappers and maps `content`→orig, `contentts`→ts, `contentroma`→roma, `Lyric_1`→lyric (`:28–34`).
3. For nonempty XML fields, uses QRC decrypter; if hex decoding raises FormatException and field is recognized as ordinary LRC, keeps plain LRC instead (`:242–265`).
4. Decrypted XML uses its `LyricContent` attribute; otherwise the decrypted string is already lyric text (`:267–280`).
5. Assigns orig to Lyrics and ts to Trans. The mapped roma field is not assigned to this return object (`:285–296`). Null is returned if both are empty.

`Lyricify.Lyrics.Decrypter.Qrc.Decrypter.DecryptLyrics` (`:12–41`) performs hex decoding, three-key DES block decryption in 8-byte blocks using the bundled static format key, zlib inflation via SharpZipLib, and optional UTF-8 BOM removal. `DESHelper.TripleDESKeySetup` (`:236–249`) and `TripleDESCrypt` (`:252–256`) show the decrypt/encrypt/decrypt key schedule composition. The report omits static format-key bytes; none are user credentials.

`Lyricify.Lyrics.Parsers.QrcParser.ParseLyricsLine` (`:52–75`) parses `text(absoluteStartMs,durationMs)`. It discards the leading line header and derives each end as start+duration. QRC timestamps inside words are absolute, unlike KRC word offsets. `Parse` additionally loads general attributes/offset before applying offset to the resulting lines (`:11–49`).

The ordinary songmid endpoint (`Api.GetLyric`, `:201–223`) returns JSONP/base64 LRC and is a separate, potentially lower-fidelity route.

## NetEase exact requested route

`Lyricify.Lyrics.Searchers.NeteaseSearcher.SearchForResults` (`:19–87`) alternates between old search and new EAPI search after exceptions or old code -460, remembering which route to try first. Ordinary search GETs `http://music.163.com/api/search/get/web` (`Netease.Api.cs:48–58`); EAPI search posts to `https://interface.music.163.com/eapi/cloudsearch/pc` (`:61–97`).

BetterLyrics calls `GetLyric`, whose exact request is form POST to `https://music.163.com/weapi/song/lyric?csrf_token=`. The payload includes id, os=pc, lv/kv/tv/rv=-1 and empty csrf_token (`Netease.Api.cs:164–179`). `Prepare` builds params from double AES-CBC encryption and encSecKey from reversed-secret RSA modular exponentiation (`:253–306`). These are request-format transforms from the shipped helper, not session-token acquisition.

The helper also exposes a different `GetLyricNew` (`:182–200`) using EAPI `/eapi/song/lyric/v1` with yv/ytv/yrv fields, and the DTO supports Lrc/Klyric/Tlyric/Romalrc/Yrc/Ytlrc/Yromalrc (`Netease.LyricResult.cs:19–31`). The original BetterLyrics provider uses Lrc/Tlyric/Romalrc only. Existence of a YRC parser/helper method does not establish that the original app calls it.

## Kugou KRC route and time semantics

`Lyricify.Lyrics.Providers.Web.Kugou.Api.GetSearchSong` (`:13–15`) retrieves up to 20 songs and `GetSearchLyrics` (`:18–29`) requests lyrics candidates from `https://lyrics.kugou.com/search` with hash and optional duration. `Decrypter.Krc.Helper.GetEncryptedLyricsAsync` (`:34–44`) fetches `https://lyrics.kugou.com/download` with candidate id/accesskey, fmt=krc, charset=utf8 and reads JSON `content`.

`Decrypter.Krc.Decrypter.DecryptLyrics` (`:16–23`) base64-decodes content, skips four header bytes, XORs the remainder with a repeating 16-byte static format key, zlib-inflates and decodes UTF-8. It unconditionally drops the first decoded character, assuming a BOM. A defensive port should remove a BOM only when present instead of dropping valid lyric text.

Decoded KRC lines are `[lineStartMs,lineDurationMs]<relativeWordStartMs,wordDurationMs,0>text...`. `KrcParser.ParseLyricsLine` (`:119–151`) computes absolute word starts as lineStart+relativeWordStart and ends by adding word duration.

`[language:...]` is base64 UTF-8 JSON. `KrcTranslationParser.GetTranslationFromKrc` (`:33–63`) selects the first content item with type 1 and takes the first string of each `lyricContent` row. Parsed translations attach by line index; `//` is cleared. KRC `Parse(string)` honors general offset (`:27–29`), while `ParseLyrics(string)` used by BetterLyrics to recover translation strings does not extract/apply it separately (`:47–63`).

## Port requirements and limitations

- Preserve absolute QRC versus relative KRC timing; do not reuse one timestamp formula for both.
- Ordinary unencrypted LRC endpoints do not reproduce the application's QQ/Kugou word timing.
- Keep HTTP adapters and parser/decode steps distinct so failures can fall through to another provider.
- BaseApi uses a shared static HttpClient and clears/replaces its default headers for each request (`BaseApi.cs:70–92`), while callers may search concurrently. Prefer per-request headers in Android; do not reproduce this concurrency hazard.
- Helper HTTP methods lack cancellation-token parameters, although the application checks cancellation after requests. Mobile requests should have bounded timeouts and track-generation guards.
- Old search URL strings use cleartext HTTP. Android's default cleartext policy blocks these; choose verified HTTPS equivalents or explicitly disable that source, rather than enabling blanket cleartext traffic.
- The helper is .NET Standard AnyCPU, so a .NET Android port could reuse it under its license/runtime requirements; a Java port must reimplement these protocols or state a narrower source scope.
- Parser upgrades can support decoded QRC/KRC/YRC and local binary KRC without claiming encrypted QQ download support. If encrypted QRC is deferred, expose a clear unsupported message rather than displaying hex as plain lyrics.

The exact decompiled files are evidence-derived reconstructions, not original source files. This report establishes no live server behavior, account entitlement or API stability.
