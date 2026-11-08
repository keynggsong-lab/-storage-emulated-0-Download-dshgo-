# DSH Go 工具

> **非官方工具**。与 DeepSeek、DSH GO 的作者无关，也不代表他们的立场。
> 这是一个针对 DSH GO 的本地配置管理器：读写它的配置文件、启停它的进程，不改它的代码。

安卓上用的小工具，把手改文件那套活收进一个 App：看状态、改提示词、装插件、启停网关、看日志。
**需要 root。**

包名 `com.asson.dshtool`，minSdk 26，targetSdk 36，Java + 手写 View（无 Kotlin、无 Compose、无第三方依赖、无 Gradle）。

## 功能

- **状态**：引擎进程、界面端口、宿主版本；关键文件是否存在、多大、最后改动时间
- **提示词**：九层配置直接编辑，保存前自动备份 `.bak-MR-MMdd-HHmm`，一键还原到最近一份
- **方案**：把当前提示词存成快照，随时一键写回；方案数量自定，可导入单文件或 zip
- **插件**：内置插件包一键注入，按宿主版本打补丁；可清洗、还原、卸载
- **网关**：代理层启停、开关切换、预热问答与触发词编辑（可选，官方账号直连不需要）
- **日志**：三份运行日志，最后 300 行，可清空（清空前留 200 行 `.prev`）

## 界面

五个页面，按使用频率排：**首页 · 提示词 · 设置 · 网关 · 日志**。

- 首页：一句话状态 + 关键文件清单（默认折叠）
- 提示词：方案槽 + 九层文件卡（默认折叠，点标题展开）+ 工作区 `AGENTS.md` 路径管理
- 设置：插件注入 / 清洗，诊断（三行结论 + 原始回显 + 一键复制），路径与 profile
- 网关：代理层端口与启停、模型地址识别与更换、开关、预热、触发词
- 日志：三份日志切换、复制最后 50 行、清空

## 提示词分层

| 层 | 作用 | 生效时机 |
|---|---|---|
| 宿主 `AGENTS.md` | 全局指令，所有工作区都用 | 新开会话 |
| 工作区 `AGENTS.md` | 只在对应工作区生效 | 新开会话 |
| `personaPrefix.txt` | 人格前缀 | 新开会话 |
| `cordis.patch.yml` | profile 补丁（模型、插件开关） | 重启 DSH GO |
| 插件 override | 插件写入的系统提示词 | 每轮组装 |
| 上游 `prompt-inject.md` | 走网关时真正发给模型的那份 | 下一条消息 |
| 兜底副本 | 网关故障时自动写回 | 故障时 |
| 网关配置 / 触发词 / 预热问答 | 网关侧行为 | 重启网关 |

## 设计约定

- **路径全部可配**，带自动探测：安卓上宿主跑在 proot 里，`/root/.dsh` 从 Android 侧不可见，
  必须填 rootfs 下的真实路径
- **写前必备份**，备份名带时间戳，界面上给出完整路径
- **所有 shell 走 `su -c`**，在 UI 线程外执行，25 秒超时强制结束
- **不联网**：没有网络权限，不上传任何内容（更新检查是可选功能，见下）
- 门户页/包名/端口都是约定，改起来集中在 `Repo.ports()` 与 `DshPaths` 两处

## 构建

不用 Gradle，`aapt2 + javac + d8 + zipalign + apksigner` 手搓：

```powershell
robocopy app D:\ace5\build_dshtool\app /MIR
powershell -ExecutionPolicy Bypass -File D:\ace5\build_dshtool\build.ps1
```

两个坑：

- `aapt2` 不吃中文路径 —— 先同步到英文目录再构建
- 必须用 `/MIR`，`/E` 会留下已删的旧 `.class`

产物 `out/DshGoTool.apk`。

**版本号**写在 `app/version.txt`，语义化三段落 `1.0.0`；`versionCode` 由脚本算：
`major*10000 + minor*100 + patch`（`1.0.0` → `10000`，`1.0.1` → `10001`）。
发新版只改这一行，`versionCode` 自动单调递增 —— 云更新比的正是它。

打包脚本还会生成 `update.json`（versionCode / versionName / url / sha256 / size / notes / minSupported），
`notes` 取自 `app/notes.txt`，下载地址按 `releases/download/v<versionName>/DshGoTool.apk` 拼。

## 签名

发布版请用自己的 keystore：

```powershell
apksigner sign --ks release.keystore --ks-key-alias <alias> --out DshGoTool.apk aligned.apk
```

**不要用 debug keystore 发版** —— 它的密码是公开的（`android`），任何人都能签出同签名包冒充更新。
从 debug 签名版迁移到正式签名，用户需要卸载一次。

## 第三方

- `app/assets/plugin-payload.tar` 内是 [dsh-purge](https://github.com/mmparko/dsh-purge) 的修改版（MIT），
  仅保留插件本体与运行状态，不携带任何提示词内容。详见 `NOTICE`
- 宿主 [@deepseek-ai/dsh](https://github.com/deepseek-ai/deepseek-harness)（MIT）不在本仓库分发
- `app/assets/gw-up.sh` / `gw-down.sh` 是可选辅助脚本，只对基于 `proxy.js` 的网关有意义

## 已知限制

- 只支持 root 环境，没 root 时只能看不能改
- 端口检测依赖 `ss`，极简 ROM 上可能误报「未监听」
- 编辑器一次性把文件读进内存，几十 KB 提示词没问题，上百 MB 日志别用它开
- 网关代理层开机不自启 —— 挂在宿主启动钩子上，要先开一次 DSH GO（设计如此）
- 方案槽存在 App 私有目录，卸载 App 才会丢；重装 APK 不影响
- **App 内置不带任何方案**，只有脚本；方案全靠用户自己存或导入

## 许可

MIT，见 `LICENSE`。
