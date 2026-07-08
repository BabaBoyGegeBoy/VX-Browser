# VX Browser

基于 **系统 WebView** 的轻量安卓浏览器（不引入 Chromium 等大体积内核），对标 X 浏览器 / Via 浏览器的体验：极简、快、隐私友好、可定制。

包名：`com.vx.browser`（取 Via + X 之意）。

## 特性

- **主页**：WebView 内渲染的 HTML 主页（Logo + 搜索框 + 扫码占位 + 书签网格），支持下拉刷新。
- **底部 5 键导航**：后退 / 前进 / 主页 / 多窗口(标签) / 菜单，无独立刷新键。
- **广告拦截**：内置精选规则子集（EasyList / AdGuard 风格），支持从文件或 URL 批量导入规则；支持 `##` 元素隐藏规则（按站点注入 `display:none`）。
- **媒体嗅探**：`shouldInterceptRequest` 捕获视频/音频/M3U8 等媒体请求，菜单「嗅探」列出直链；主动探测类型/大小/分辨率（解析 M3U8 多清晰度、MP4 宽高）。
- **手动标记广告**：页面「标记广告」模式，点击元素高亮、上下级 DOM 微调、多选后隐藏/删除，按站点存入 Room，复访自动生效。
- **弹窗 / 自动跳转防护**：设置开关「阻止弹窗」「阻止自动跳转」（拦截非用户手势触发的跳转）。
- **单页跳转确认**：可对当前站点开启「跳转确认」，异站跳转前弹框确认，防止误点广告链接。
- **嗅探列表筛选**：按扩展名 / 大小 / 类型 三维度叠加筛选，帮助快速定位所需链接。
- **三方下载绑定**：嗅探到的链接可一键用 IDM+ 等外部下载器拉起。

## 构建

环境要求：

- JDK 17+（已验证 JDK 21）
- Android SDK（compileSdk / targetSdk 36，minSdk 34）
- Gradle 8.11.1（项目自带 wrapper）

步骤：

```bash
# 1. 首次需 bootstrap Gradle wrapper（若 gradle-wrapper.jar 缺失）
powershell -ExecutionPolicy Bypass -File get-wrapper.ps1

# 2. 连上设备（无线 adb 或 USB）
adb devices

# 3. 编译并安装
./gradlew installDebug        # Windows: gradlew.bat installDebug
```

> 国内环境已在 `settings.gradle.kts` 配置阿里云 Maven 镜像、在 `gradle-wrapper.properties` 配置腾讯云 Gradle 镜像。

## 权限

- 网络访问（`INTERNET`）：浏览器基本能力。
- 存储（`READ/WRITE_EXTERNAL_STORAGE`，旧机型）：保存下载/历史等。
- 摄像头（可选，扫码功能待实现）：仅用于未来真机扫码。

## 模块简览

| 模块 | 说明 |
| --- | --- |
| `browser/` | 标签管理、WebView 客户端（广告拦截 / 媒体嗅探 / 元素选择器注入 / 弹窗与跳转处理）、AdBlock 引擎、MediaSniffer / MediaProbe |
| `data/` | Room：书签、历史、站点级元素规则（entity / dao / repository） |
| `ui/` | 主页、书签、历史、设置 Fragment |
| `util/Prefs` | SharedPreferences 封装（各开关与站点级配置） |

## 状态

部分功能仍在打磨中（如广告拦截规则覆盖度、真机扫码）。欢迎在 Issues 中反馈。

## License

待定（TODO）。
