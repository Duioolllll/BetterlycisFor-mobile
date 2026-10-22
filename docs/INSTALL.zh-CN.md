# 安装说明 — 简体中文

[한국어](INSTALL.ko.md) · [English](INSTALL.en.md) · [项目主页](../README.en.md)

## 1. 安装 APK

需要 **Android 8.0 或更高版本**。请使用 Spotify、YouTube Music、Apple Music、三星音乐等其他应用播放音乐。音乐识别取决于播放器是否提供 Android 媒体会话。应用界面目前为韩语，以下保留实际按钮名称供对照。

1. **[下载 0.3.0 APK](https://github.com/sdcom1214/BetterlycisFor-mobile/raw/refs/heads/main/downloads/BetterLyrics-Mobile-0.3.0.apk)**。如果打开 GitHub 文件页面，请点击 **Download raw file**。**Code → Download ZIP** 下载的是源码，不是安装包。
2. 打开 `BetterLyrics-Mobile-0.3.0.apk`。若系统要求确认安装来源，请查看打开文件的浏览器或文件管理器的 **设置 → 允许来自此来源的应用**，然后返回安装。不同设备的菜单名称可能不同。
3. 安装后打开 BetterLyrics Mobile。之后可撤销安装来源权限。

文件大小为 **103,221 字节**。可核对 [SHA-256](../downloads/SHA256SUMS) 和[签名信息](../downloads/README.md)。校验一致仅用于识别发布文件，不代表通过安全审核。

## 2. 连接音乐识别

1. 点击右上角 **··· → 음악 감지**（音乐识别）。
2. 在 Android 通知使用权设置中选择 BetterLyrics，阅读权限说明后允许。
3. 在音乐应用中播放歌曲，再返回 BetterLyrics。若有多个播放器，点击右下角列表按钮 **재생 앱 선택**（选择播放器）。

如果**安装后**通知使用权受到限制，且您信任应用及其权限用途，可检查系统是否提供 **设置 → 应用 → BetterLyrics Mobile → 更多 → 允许受限设置**。是否提供此菜单取决于系统和设备。这与安装时的 Play Protect 拦截是不同的问题。

## 3. 可选功能

| 功能 | 操作 |
| --- | --- |
| 悬浮歌词 | **··· → 플로팅 가사**；按需授予显示在其他应用上层的权限 |
| 声音响应背景 | 底部中央波形按钮，或 **··· → 사운드와 배경 → 재생 소리에 연결** |
| 其他音频连接方式 | 默认方式失败时使用 **다른 방식으로 소리 연결**；需要 Android 10 以上及系统授权 |
| 歌词/专辑视图切换 | 左下角引号按钮 |
| 当前歌曲时间校正 | **··· → 이 곡의 가사 시간 보정**；`+` 提前显示，`−` 延后显示 |

系统可能请求音频权限，但此实现不会打开麦克风输入。音乐应用限制播放音频捕获时，仍可使用默认渐变背景。歌词识别不强制要求音频连接。

## 4. 更新与常见问题

- **更新：** 包名及签名证书相同的 APK 可以覆盖安装。首次从源码构建会生成您自己的密钥，与下载 APK 的签名不同。签名不同时可能需要先卸载旧应用，导致设置和导入的歌词被删除。当前版本没有外部备份功能。
- **无法下载：** 使用普通浏览器打开 APK 链接，或在 GitHub 文件页面点击 **Download raw file**。不要把网页或源码 ZIP 改名为 APK。
- **Play Protect 阻止安装：** 停止安装并查看警告。本试验版不宣称已通过 Google 安全审核，安装步骤不要求关闭 Play Protect。如属误判，开发者应查看 Google 的申诉流程。
- **没有歌曲或封面：** 检查通知使用权，在音乐应用中开始播放，再重新选择播放器。播放器未提供的数据无法显示。
- **歌词或时间不匹配：** 在 **···** 中手动搜索并选择相同歌曲和版本，或导入本地歌词。固定时间差可使用歌曲时间校正；源歌词缺少或包含错误时间戳时，无法保证自动修复。

尚未在实体 Android 设备上验证安装、权限及音乐应用联动。请查看[验证范围](VALIDATION.md)。

## Android 官方帮助

- [从其他来源安装应用](https://support.google.com/pixelphone/answer/7391672?hl=zh-Hans)
- [受限设置](https://support.google.com/android/answer/12623953?hl=zh-Hans)
- [Google Play Protect](https://support.google.com/googleplay/answer/2812853?hl=zh-Hans)
