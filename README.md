# 工作沙盒 (WorkSandbox)

## 这是什么

一个极简的 Android 应用，支持系统「创建工作资料（Work Profile）」流程，以及工作资料内的未知来源安装限制开关。

不冻结/解冻应用，不做统计分析，不做跨资料文件转发，不依赖任何第三方开源库或工具（如 Island / Insular / Shelter）。所有逻辑均直接调用 Android 官方公开 API：`android.app.admin.DevicePolicyManager`。

创建完成后，设备上会出现一个独立的「工作资料」沙盒（系统内部是一个新的 Android 用户身份），可在其中单独安装应用（例如 Facebook、Instagram、Messenger、WhatsApp），与个人空间、三星「安全文件夹」三者完全独立、互不影响。

**重要：工作资料由系统 `system_server` 维护，一旦创建完成，即使卸载本应用，工作资料依然存在。** 如需移除，请到「设置 - 账户与备份 - 管理用户」或「设置 - 安全和隐私 - 设备管理应用」中操作，或在本 App 内点击对应入口跳转设置页查看。

## 使用方法

1. 安装 APK（见下方构建说明，或从 GitHub Actions 构建产物下载）
2. 打开 App，点击「创建工作模式（工作资料）」
3. 接下来会进入 **Android 系统自带**的引导界面（非本 App 绘制），按提示完成
4. 完成后，桌面会出现带「公文包」角标的应用图标区域，这就是工作资料
5. 在工作资料内打开 Play 商店，搜索并安装需要隔离的应用

## 权限说明

- `android.permission.BIND_DEVICE_ADMIN`：系统强制要求，仅系统本身可触发设备管理员组件，第三方应用无法伪造调用
- INTERNET：仅在主动点击“检查更新”时查询官方 GitHub 发布元数据；下载交给浏览器。不读取通讯录、位置或存储。

## 本地构建

```bash
git clone https://github.com/hubg9527777-dotcom/WorkSandbox.git
cd WorkSandbox
./gradlew --version   # 使用仓库提供的 Wrapper
./gradlew assembleDebug
```

生成的 APK 位于 `app/build/outputs/apk/debug/app-debug.apk`。

## 发布新版本（触发 GitHub Actions 自动构建）

本仓库的 `.github/workflows/release.yml` 由 **Git tag 推送**触发，不是普通的代码推送：

```bash
git tag -a v1.0.4 -m "首个版本"
git push origin v1.0.4
```

推送 tag 后：

1. 进入仓库 **Actions** 标签查看构建进度
2. 构建成功后，进入仓库 **Releases** 页面，会看到自动创建的 Release，附带：
   - `WorkSandbox-v1.0.4.apk`（构建产物）
   - "Verified" 标记（Artifact Attestation，证明该文件确实由本仓库的 CI 构建，未被篡改）
3. 下载该 APK 传到手机安装（需在系统设置中临时允许「安装未知来源应用」）

### 验证 APK 来源（可选，供下载者使用）

```bash
gh attestation verify WorkSandbox-v1.0.4.apk --repo hubg9527777-dotcom/WorkSandbox
```

验证成功表示该文件确实由本仓库的官方 CI 构建，未经篡改。

## 已知限制

- 仅做最基础的工作资料创建，不包含应用冻结、统计、跨资料分享等增强功能
- 对外发布使用原有 Release 签名；Debug APK 仅供测试
- 三星设备 One UI 对工作资料的呈现可能与原生 Android 略有差异（图标角标样式、设置入口路径），属正常现象

## 1.0.4：工作资料安装未知应用

新建工作资料默认关闭未知来源安装。打开带公文包图标的“工作沙盒”，开启“允许工作资料安装未知应用”并确认，再点击“为安装来源授权”，允许工作资料中的文件管理器或浏览器安装 APK。关闭开关会恢复限制，不移除已安装应用。升级既有工作资料时保留当前策略，不自动覆盖用户选择。

该开关只解除本应用对工作资料的限制；无法绕过其他管理员、设备全局限制或三星自动拦截器。若系统没有授权入口，可在工作资料中打开 APK 按系统提示授权。各厂商行为仍需真机验证。

发布：`bash scripts/publish-v1.0.4.sh`（沿用原签名密钥，交互确认后提交、推送并发布 v1.0.4）。

### 以后如何升级

点击“检查更新”，查看新版本和发布说明，再选择下载官方 APK；下载后打开文件，选择“更新”。保持包名与原签名密钥不变，每次发布递增 versionCode。不要先卸载旧版或移除工作资料，覆盖更新保留既有数据和安装策略。程序代码在 Android 用户空间间共享，个人与工作资料的数据保持隔离。

检查更新只访问固定仓库的 HTTPS API，不携带 Token，不后台轮询；系统浏览器处理下载，Android 安装器核验签名并要求安装确认。网络、GitHub 限流或尚未上传完 APK 时可稍后重试。当前版本采用三段数字版本格式。1.0.3 没有检查按钮，首次升级到 1.0.4 需要手动下载。
