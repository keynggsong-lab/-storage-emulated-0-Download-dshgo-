# Build DSH Go Tool without Gradle: aapt2 + javac + d8 + zipalign + apksigner.
# Reuses the toolchain already present at D:\ace5\android-build.
$ErrorActionPreference = "Stop"

$JK   = "D:\ace5\android-build\jdk"
$BT   = "D:\ace5\android-build\build-tools\android-14"
$PLAT = "D:\ace5\android-build\platform-36\android-36\android.jar"
$KS   = "D:\ace5\android-build\release.keystore"
$KSPW = "D:\ace5\android-build\release.keystore.pass"
$ALIAS = "dshtool"
# 发布用的仓库标识，update.json 里的下载地址按它拼
$REPO = "keynggsong-lab/-storage-emulated-0-Download-dshgo-"

$ROOT = Split-Path -Parent $MyInvocation.MyCommand.Path
$APP  = Join-Path $ROOT "app"
$OUT  = Join-Path $ROOT "out"
$APK  = Join-Path $OUT "DshGoTool.apk"

$env:JAVA_HOME = $JK
$env:PATH = "$JK\bin;$BT;$env:PATH"

Write-Output "=== 0) prep ==="
if (Test-Path $OUT) { Remove-Item $OUT -Recurse -Force }
New-Item -ItemType Directory -Path "$OUT\classes", "$OUT\dex", "$OUT\gen" -Force | Out-Null

Write-Output "=== 1) aapt2 compile res ==="
& "$BT\aapt2.exe" compile --dir "$APP\res" -o "$OUT\res.zip"
if ($LASTEXITCODE -ne 0) { throw "aapt2 compile failed" }

# 版本号从 version.txt 读（语义化：1.0.0）。versionCode = major*10000 + minor*100 + patch，
# 必须是整数且单调递增；发新版只改 version.txt 一行。
$VERFILE = Join-Path $APP "version.txt"
if (-not (Test-Path $VERFILE)) { throw "missing version.txt（内容形如 1.0.0）" }
$VNAME = (Get-Content $VERFILE -Raw).Trim()
if ($VNAME -notmatch '^(\d+)\.(\d+)\.(\d+)$') { throw "version.txt 格式不对：$VNAME（要 1.0.0 这样）" }
$VCODE = [int]$Matches[1] * 10000 + [int]$Matches[2] * 100 + [int]$Matches[3]
Write-Output ("  version: " + $VNAME + " (" + $VCODE + ")")
Write-Output "=== 2) aapt2 link ==="
& "$BT\aapt2.exe" link -o "$OUT\base.apk" -I $PLAT --manifest "$APP\AndroidManifest.xml" `
  "$OUT\res.zip" --java "$OUT\gen" -A "$APP\assets" `
  --min-sdk-version 26 --target-sdk-version 36 --version-code $VCODE --version-name $VNAME
if ($LASTEXITCODE -ne 0) { throw "aapt2 link failed" }

Write-Output "=== 3) javac ==="
$srcs = Get-ChildItem "$APP\src" -Recurse -Filter *.java | ForEach-Object { $_.FullName }
$gen  = Get-ChildItem "$OUT\gen" -Recurse -Filter *.java -ErrorAction SilentlyContinue | ForEach-Object { $_.FullName }
Write-Output ("  sources: " + $srcs.Count + "  generated: " + $gen.Count)
& "$JK\bin\javac.exe" -encoding UTF-8 -source 8 -target 8 -bootclasspath $PLAT -classpath $PLAT `
  -d "$OUT\classes" @($srcs + $gen)
if ($LASTEXITCODE -ne 0) { throw "javac failed" }

Write-Output "=== 4) d8 ==="
$classes = Get-ChildItem "$OUT\classes" -Recurse -Filter *.class | ForEach-Object { $_.FullName }
Write-Output ("  class files: " + $classes.Count)
# 直接把这些路径拼进命令行会超 Windows 8191 字符上限（109 个 class 就炸）。
# 先打成一个 jar，d8 接受 jar 作为输入。
& "$JK\bin\jar.exe" cf "$OUT\classes.jar" -C "$OUT\classes" .
if ($LASTEXITCODE -ne 0) { throw "jar failed" }
& "$BT\d8.bat" --lib $PLAT --min-api 26 --output "$OUT\dex" "$OUT\classes.jar"
if ($LASTEXITCODE -ne 0) { throw "d8 failed" }

Write-Output "=== 5) inject dex ==="
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [System.IO.Compression.ZipFile]::Open((Join-Path $OUT "base.apk"), "Update")
$dexPath = Join-Path $OUT "dex\classes.dex"
if (Test-Path $dexPath) {
  $entry = $zip.Entries | Where-Object { $_.FullName -eq "classes.dex" }
  if ($entry) { $entry.Delete() }
  [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip, $dexPath, "classes.dex", "Optimal") | Out-Null
}
$zip.Dispose()
Write-Output "  dex injected"

Write-Output "=== 6) zipalign ==="
& "$BT\zipalign.exe" -p -f 4 "$OUT\base.apk" "$OUT\aligned.apk"
if ($LASTEXITCODE -ne 0) { throw "zipalign failed" }

Write-Output "=== 7) sign ==="
if (-not (Test-Path $KS)) { throw "missing release keystore: $KS（不要用 debug keystore 发版）" }
if (-not (Test-Path $KSPW)) { throw "missing keystore password file: $KSPW" }
$PW = (Get-Content $KSPW -Raw).Trim()
& "$BT\apksigner.bat" sign --ks $KS --ks-pass "pass:$PW" --key-pass "pass:$PW" `
  --ks-key-alias $ALIAS --out $APK "$OUT\aligned.apk"
if ($LASTEXITCODE -ne 0) { throw "apksigner failed" }

Write-Output "=== 8) verify ==="
& "$BT\aapt2.exe" dump badging $APK | Select-Object -First 3
Write-Output ("APK -> " + $APK + "  " + [math]::Round((Get-Item $APK).Length / 1KB) + " KB")

Write-Output "=== 9) update.json ==="
$SHA = (Get-FileHash $APK -Algorithm SHA256).Hash.ToLower()
$UPD = [ordered]@{
  versionCode  = [int]$VCODE
  versionName  = $VNAME
  url          = "https://github.com/$REPO/releases/download/v$VNAME/DshGoTool.apk"
  sha256       = $SHA
  size         = (Get-Item $APK).Length
  notes        = $(if (Test-Path (Join-Path $APP "notes.txt")) { (Get-Content (Join-Path $APP "notes.txt") -Raw).Trim() } else { "" })
  minSupported = [int]([int]$VCODE - 100)
}
$json = $UPD | ConvertTo-Json
$utf8 = New-Object System.Text.UTF8Encoding($false)
[System.IO.File]::WriteAllText("$OUT\update.json", $json, $utf8)
# 源码仓库里也放一份，方便 git 提交后直接生效
$REPO_ROOT = "D:\dsh桌面版\A\DshGoTool"
if (Test-Path $REPO_ROOT) { Copy-Item "$OUT\update.json" (Join-Path $REPO_ROOT "update.json") -Force }
Write-Output ("update.json  sha256=" + $SHA)
