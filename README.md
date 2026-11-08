# DSH Go 工具

> **非官方工具**。与 DeepSeek、DSH GO 的作者无关，也不代表他们的立场。
> 这是一个针对 DSH GO 的本地配置管理器，通过读写其配置文件、启停其进程工作。

给安卓上跑 dsh go 的机器用的 root 工具箱。改提示词、控网关、看日志，一个 App 搞定。

包名 `com.asson.dshtool`，minSdk 26，targetSdk 36，Java + 手写 View（无 Kotlin、无 Compose、无第三方依赖）。

## 它能干什么

**状态页** —— 7863 / 7864 / 7865 三个端口谁在监听、pid 多少；六个关键文件是否存在、大小、最后修改时间；wb2api、proxy.js、node、dsh 相关进程列表。

**提示词页** —— 直接编辑这九个文件：

```
wb2api 的 prompt-inject.md      → 真正发给模型的 system
dsh 的 prompt-inject.md         → dsh-purge override
AGENTS.md                       → 工作区指令
personaPrefix.txt               → 人格前缀
cordis.patch.yml                → profile 补丁
wb2api 的 config.json           → 网关配置
trigger.txt                     → proxy 触发词
warm.json                       → proxy 预热问答
```

保存前自动备份成 `<文件>.bak-MR-<MMdd-HHmm>`，一键还原到最近一份备份。编辑器是全屏等宽字体，改长文本不憋屈。

**网关页** —— 启动 / 停止 / 重启 `gw-up.sh` / `gw-down.sh`；**11 个 gw.env 开关**全部可改（布尔开关点一下就切，数值开关弹输入框），改完先备份再问你要不要立刻重启；**预热提示词**按问答对结构化编辑（可增删改）；**触发词**整表编辑。输出直接显示在页面里。

**日志页** —— tail gw.log / guard.log / wb2api run.log，最后 300 行。

**设置页** —— 四个路径可改，带自动探测。安卓上的 dsh go 跑在 proot 里，`/root/.dsh` 从 Android 侧看不见，必须填 rootfs 下的真实路径，探测按钮会扫常见位置。

## 构建

工程没有带 `gradle-wrapper.jar`（二进制不好随源码走），用下面任一方式：

工程实际用 **aapt2 + javac + d8 + zipalign + apksigner 手搓**，不用 Gradle。

**注意：`aapt2` 不吃中文路径。** 源码在 `D:\dsh桌面版\A\DshGoTool`，构建要在英文路径下做：

```powershell
# 1) 同步源码到英文构建目录
robocopy "D:\dsh桌面版\A\DshGoTool\app" "D:\ace5\build_dshtool\app" /E
copy "D:\dsh桌面版\A\DshGoTool\build.ps1" "D:\ace5\build_dshtool\build.ps1"

# 2) 构建
powershell -ExecutionPolicy Bypass -File D:\ace5\build_dshtool\build.ps1
```

产物 `D:\ace5\build_dshtool\out\DshGoTool.apk`。

版本号按构建时间自增：`versionCode = yyMMddHH`，`versionName = 1.MMdd.HHmm`，装新版能看出区别。

工具链复用 `D:\ace5\android-build`（JDK + build-tools android-14 + platform-36 + debug.keystore）。

## 安装与授权

```bash
adb install -r app-debug.apk
```

第一次打开会弹 `su` 授权，给永久权限。App 本身不申请任何危险权限，所有落盘和进程操作都通过 `su -c` 完成。

## 首次配置

打开「设置」，先点「探测」。它会扫：

```
/data/data/*/files/rootfs/root/.dsh
/data/local/tmp/*/rootfs/root/.dsh
/data/adb/gw, /data/adb/dshgw
/data/adb/wb2api, /data/adb/wb2api-guard
```

把探测到的路径填进四个输入框，保存。填错了「状态」页会显示文件缺失，一眼能看出来。

## 路径说明

| 项 | 典型值 | 说明 |
|---|---|---|
| `wb2api 目录` | `/data/adb/wb2api` | 放 wb2api 二进制、`config.json`、`prompt-inject.md` |
| `guard 目录` | `/data/adb/wb2api-guard` | 守护脚本与 guard.log |
| `gw 目录` | `/data/adb/gw` | `proxy.js`、`trigger.txt`、`warm.json`、`gw.env` |
| `DSH_HOME` | rootfs 里的 `.../root/.dsh` | `AGENTS.md`、`prompt-inject.md`、`profiles/` |

## 安全边界

App 只做四件事：读文件、写文件（写前备份）、执行 gw 启停脚本、改 gw.env 里指定键。没有联网上传，没有后台服务，没有自启动。所有 shell 命令都走 `su -c` 并在 UI 线程外执行，超时 25 秒强制结束。

写文件用 base64 中转，绕开引号、反引号、`$` 在 shell 里的转义问题——提示词里全是这些字符，直接 `echo` 会炸。

## 已知限制

- 只认 root 环境，没有 root 会在启动页直接挡住。
- 端口检测依赖 `ss` 或 `netstat`，两者都没有的极简 ROM 上会显示"未监听"，实际可能只是检测不到。
- 编辑器一次性把整个文件读进内存，几十 KB 的提示词没问题，上百 MB 的日志别用它开（用日志页 tail）。
- gw 目录里的 `gw-up.sh` / `gw-down.sh` 名字写死了，脚本改名的话启动/停止会失败，但重启按钮用的命令有 `./` 回退。


## 构建

不用 Gradle，`aapt2 + javac + d8 + zipalign + apksigner` 手搓：

```powershell
robocopy app D:\ace5\build_dshtool\app /MIR
powershell -ExecutionPolicy Bypass -File D:\ace5\build_dshtool\build.ps1
```

两个坑：

- `aapt2` 不吃中文路径 —— 先同步到英文目录再构建
- 必须用 `/MIR`，`/E` 会留下已删的旧 `.class`

产物 `out/DshGoTool.apk`，`versionCode` 按 `yyMMddHH` 自增。

## 签名

发布版请用自己的 keystore，并在 `build.ps1` 里把 `-ks` 指过去：

```powershell
apksigner sign --ks release.keystore --ks-key-alias <alias> --out DshGoTool.apk aligned.apk
```

**不要用 debug keystore 发版** —— 它的密码是公开的（`android`），任何人都能签出同签名包冒充更新。
从旧版（debug 签名）迁移到正式签名，用户需要卸载一次。

## 第三方

- `assets/plugin-payload.tar` 内是 [dsh-purge](https://github.com/mmparko/dsh-purge) 的修改版（MIT），
  详见 `NOTICE`
- 宿主 [@deepseek-ai/dsh](https://github.com/deepseek-ai/deepseek-harness)（MIT）不在本仓库分发

## 许可

MIT，见 `LICENSE`。
