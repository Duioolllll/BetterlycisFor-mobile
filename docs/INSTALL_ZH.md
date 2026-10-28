# BetterLyrics Mobile 安装指南 — 简体中文

[한국어](INSTALL_KO.md) · [English](INSTALL_EN.md) · [简体中文](INSTALL_ZH.md) · [首页](../README.md)

**0.9.1 · Android8.0及以上(API26+) · 包名 `io.betterlyrics.mobile` · versionCode19**
应用界面目前为韩语；翻译安装指南不代表提供英语或中文界面。这是独立的非官方项目。面向Android16构建，但0.9.1发布前须通过Android16模拟器安装、启动、升级检查；真机和音乐播放仍需单独验证。参见[验证范围](VALIDATION.md)。

## A. 下载与校验

1. 打开[GitHub仓库](https://github.com/Duioolllll/BetterlycisFor-mobile)。
2. 选择 **Releases**。
3. 打开 **Latest Release**，或使用[最新版本链接](https://github.com/Duioolllll/BetterlycisFor-mobile/releases/latest)。
4. 展开 **Assets** 并下载APK。`Source code.zip/tar.gz`不是安装包。
5. 本次文件名为 `BetterLyrics-Mobile-0.9.1.apk`。后续版本以相应Release的版本和文件名为准。
6. 如有条件，将下载文件的SHA-256与附带的`SHA256SUMS`比较。不一致时不要安装，请重新下载。

[0.9.1 APK](https://github.com/Duioolllll/BetterlycisFor-mobile/releases/download/v0.9.1/BetterLyrics-Mobile-0.9.1.apk) · [校验和](../downloads/SHA256SUMS)。Release暂时无法打开时，可使用[仓库中的APK](https://github.com/Duioolllll/BetterlycisFor-mobile/raw/refs/heads/main/downloads/BetterLyrics-Mobile-0.9.1.apk)。不要把HTML网页或源码ZIP改名为APK。

在电脑上校验：

```powershell
Get-FileHash .\BetterLyrics-Mobile-0.9.1.apk -Algorithm SHA256
```

Linux使用`sha256sum BetterLyrics-Mobile-0.9.1.apk`，macOS使用`shasum -a 256 BetterLyrics-Mobile-0.9.1.apk`。校验和、签名一致用于识别文件，不代表通过Google安全审核。

## B. 在Android上安装

1. 打开下载的APK。
2. 仅在系统要求时，为打开该文件的浏览器或文件管理器启用 **允许来自此来源的应用**，然后返回。
3. 在安装界面确认 **BetterLyrics Mobile** 名称和显示的权限。
4. 完成安装。通知使用权、音频等特殊权限可能在首次使用相应功能时另行请求。
5. 安装完成后，可关闭不再需要的来源安装权限。

Google Play Protect出现警告时，请阅读具体内容和原因。如果判定为有害应用或提示“为保护设备已阻止安装”，请停止安装，并向本仓库报告警告和版本。本项目不声称通过Google审核。不要强制关闭安全功能，也不要无条件忽略警告。

## C. 首次启动与使用

1. 启动应用，点击右上角 **··· → 음악 감지**（音乐检测），阅读Android的通知使用权说明后允许。该权限用于获取其他音乐应用的媒体会话和歌曲信息。
2. 先在Spotify、YouTube Music、Apple Music、三星音乐或其他音乐应用中播放歌曲。BetterLyrics不是音乐流媒体服务，也不要求音乐账号登录。
3. 返回后会自动搜索歌词。若有多个会话，点击底部列表按钮 **재생 앱 선택**（选择播放应用）。音乐应用必须公开标准Android MediaSession。列出这些应用不代表已全部通过真机测试。
4. 对照实际歌曲检查高亮行。如歌词版本不同，在 **···** 中使用 **가사 직접 찾기**（手动搜索）、**다시 검색**（重新搜索）或 **가사 파일 불러오기**（导入歌词）。没有时间信息的普通文本无法精确同步。
5. 固定偏差可通过 **이 곡의 가사 시간 보정**（当前歌曲时间校正）调整。`+`让歌词更早显示，`−`更晚显示。跳转、暂停后再次检查。
6. 在 **··· → 화면 꾸미기**（外观设置）选择原版风格/歌词专注/专辑优先，调整布局、对齐、字号/粗细、间距、模糊、亮度、封面、颜色及控件。修改立即生效并保存。重置预设不会删除歌曲时间校正。悬浮歌词使用独立样式。
7. 在 **사운드와 배경**（声音与背景）选择声音响应/流动/静态模式和强度。声音响应需要点击 **재생 소리에 연결**（连接播放声音）并允许音频权限。默认方式失败时，Android10以上可用 **다른 방식으로 소리 연결**（其他连接方式），并确认系统共享提示。

音频权限用于分析播放声音；此实现不打开麦克风输入，不保存或上传录音。音乐应用禁止捕获时保留基础渐变。显示歌词不强制要求声音连接，离开应用界面后停止分析。仅悬浮歌词需要 **显示在其他应用上层** 权限。文件导入只读取用户选定的文档。

若通知使用权因“受限设置”锁定，这与安装被拦截不同。先判断是否信任安装的应用及权限，再按照Android应用信息中的受限设置说明操作。不要绕过组织或家长策略。

## D. 更新

从GitHub Releases下载最新APK，覆盖安装。**包名和签名必须一致，versionCode不能降低。** 本次已核对与上一发行版的证书一致。自行构建的debug包或新密钥可能不同。

更新失败时检查校验和、版本、包名和签名。签名不一致时，应获取原发行者使用原密钥签署的APK。卸载后重装仅作为最后选择：设置、歌曲偏移、已导入歌词副本和缓存可能丢失。应用没有外部备份/导出功能，请另外保存原歌词文件和需要的设置。

## E. 常见问题

| 问题 | 检查与处理 |
| --- | --- |
| 0.4.0安装失败 | 已确认资源打包缺陷，请重新下载0.9.1，不要仅修改旧文件名 |
| APK安装失败 | 检查Android8以上、剩余空间、完整下载/校验和及正确APK；更新还需检查签名和版本 |
| 安装被阻止 | 检查打开文件的应用所对应的来源权限；组织/家长策略请咨询管理员 |
| Play Protect警告 | 阅读警告，有害应用判定时停止；不要强制关闭保护；报告完整提示和版本 |
| 启动即崩溃或白屏 | 更新Android System WebView/Chrome后重开；仍有问题请提供设备、Android、应用版本及脱敏ADB日志。尚未证明真机无崩溃 |
| 无法识别音乐应用/断开连接 | 检查通知使用权、实际播放、选择播放应用→刷新。返回应用会请求重连。未提供MediaSession和省电策略仍可能影响 |
| 无歌词/搜索很久 | 检查网络和提供方，尝试其他提供方、手动搜索或本地文件。有超时限制，但多个候选可能需要几十秒 |
| 韩语歌词不同步 | 选择标题、歌手、专辑版本、时长一致的候选。固定偏差使用歌曲校正；不规则偏差可能是源时间戳错误，应换歌词或本地文件。不能自动修复所有歌曲 |
| 不显示专辑封面 | 播放后重新选择应用并检查网络。会尝试元数据、URI及匹配会话的通知封面，但源应用必须提供图片和访问权 |
| 背景效果不生效 | 检查模式、强度、亮度和声音连接。静态、减少动态效果、暂停、静音、禁止捕获、无WebGL时会减少效果或改用渐变 |
| Android权限错误 | 分别检查通知使用权、悬浮窗、音频和共享同意。只重新开启需要的功能，不必无条件允许所有权限 |
| Android16兼容问题 | 仅完成target36构建及代码检查。更新WebView后报告权限、系统栏、旋转、共享停止时机。锁屏或离开应用后可能需要重新同意声音共享 |

[官方安装说明](https://support.google.com/pixelphone/answer/7391672) · [受限设置](https://support.google.com/android/answer/12623953) · [Play Protect](https://support.google.com/googleplay/answer/2812853)
