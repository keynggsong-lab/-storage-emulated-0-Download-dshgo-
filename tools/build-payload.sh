#!/bin/bash
# 把 dsh-purge 便携包裁成 App 内置 payload。
# 用法: bash build-payload.sh <便携包目录> <输出 assets 目录>
# 例:   bash build-payload.sh /c/Users/Administrator/Desktop/dsh-purge /d/dsh桌面版/A/DshGoTool/app/assets
set -e

SRC="${1:?需要便携包目录（含 payload/）}"
OUT="${2:?需要输出 assets 目录}"

# 兼容两种输入：便携包根目录（含 payload/）或直接给 payload 目录
if [ -d "$SRC/payload" ]; then PAY="$SRC/payload"; else PAY="$SRC"; fi
[ -d "$PAY/plugins/dsh-purge" ] || { echo "找不到 $PAY/plugins/dsh-purge"; exit 1; }

STAGE="$(mktemp -d)"
trap 'rm -rf "$STAGE"' EXIT

# ---- 插件本体：砍掉文档与预览图（docs 里 700KB 全是 PNG/HTML，运行时用不到）----
mkdir -p "$STAGE/plugins"
cp -r "$PAY/plugins/dsh-purge" "$STAGE/plugins/dsh-purge"
cd "$STAGE/plugins/dsh-purge"
rm -rf docs
rm -f README.md README.zh-CN.md release-notes.md screenshots.json \
      .gitignore .npmignore ADAPT-NOTES.md
cd "$STAGE"

# ---- 提示词 ----
mkdir -p "$STAGE/dsh"
cp "$PAY/dsh/AGENTS.md"          "$STAGE/dsh/AGENTS.md"
cp "$PAY/dsh/prompt-inject.md"   "$STAGE/dsh/prompt-inject.md"
cp "$PAY/dsh/personaPrefix.txt"  "$STAGE/dsh/personaPrefix.txt"

# ---- 状态文件（有就带）----
if [ -d "$PAY/state" ]; then
  mkdir -p "$STAGE/state"
  cp "$PAY/state/"*.json "$STAGE/state/" 2>/dev/null || true
fi

# ---- 元信息 ----
VER=$(sed -n 's/.*"version": *"\([^"]*\)".*/\1/p' "$STAGE/plugins/dsh-purge/package.json" | head -1)
{
  printf '{\n'
  printf '  "plugin": "dsh-purge",\n'
  printf '  "version": "%s",\n' "$VER"
  printf '  "built": "%s",\n' "$(date '+%Y-%m-%d %H:%M')"
  printf '  "profileDep": "file:../../plugins/dsh-purge",\n'
  printf '  "bundle": "dsh-purge"\n'
  printf '}\n'
} > "$STAGE/meta.json"

# ---- 打 tar（Android 侧 toybox tar 可直接解）----
mkdir -p "$OUT"
tar -cf "$OUT/plugin-payload.tar" -C "$STAGE" meta.json plugins dsh state

echo "version: $VER"
echo "files  : $(tar -tf "$OUT/plugin-payload.tar" | grep -vc '/$')"
echo "size   : $(du -k "$OUT/plugin-payload.tar" | cut -f1) KB"
echo "output : $OUT/plugin/payload.tar"
