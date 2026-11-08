#!/bin/bash
# 停网关 + 看门狗。杀完必须复核端口，杀不掉要喊出来，不许静默成功。
#
# 复核用「端口是否还在 LISTEN」，不能用 curl：
#   进程被 STOP / 卡死时端口照样占着但不回包，curl 会返回 000 —— 拿它当"已停"是误判，
#   结果 SIGKILL 那一步永远走不到，端口一直被幽灵进程占着。
GW="$(cd "$(dirname "$0")" 2>/dev/null && pwd)"
[ -z "$GW" ] && GW=/root/workspace/gw
PORT=7864
if [ -f "$GW/gw.env" ]; then
  set -a; . "$GW/gw.env"; set +a
  [ -n "$GW_PORT" ] && PORT="$GW_PORT"
fi

port_busy() {
  hex=$(printf ':%04X' "$PORT")
  awk -v p="$hex" '$4=="0A" && index($2,p)>0 {found=1} END {exit !found}' \
    /proc/net/tcp /proc/net/tcp6 2>/dev/null
}

# 1. 看门狗
if [ -f "$GW/watchdog.pid" ]; then
  kill "$(cat "$GW/watchdog.pid")" 2>/dev/null && echo "看门狗已停"
  rm -f "$GW/watchdog.pid"
fi
pkill -f "$GW/watchdog.sh" 2>/dev/null

# 2. 网关：先 pid 文件，再命令行匹配
PID=""
if [ -f "$GW/gw.pid" ]; then
  PID="$(cat "$GW/gw.pid" 2>/dev/null)"
  [ -n "$PID" ] && kill "$PID" 2>/dev/null
fi
pkill -f "$GW/proxy.js" 2>/dev/null

# 3. 复核 + 升级：SIGTERM 对 STOP/D 状态进程无效，第二轮必须 SIGKILL
n=0
while port_busy && [ $n -lt 6 ]; do
  n=$((n+1))
  if [ $n -eq 2 ]; then
    echo "SIGTERM 没杀掉，升级到 SIGKILL"
    [ -n "$PID" ] && kill -9 "$PID" 2>/dev/null
    pkill -9 -f "$GW/proxy.js" 2>/dev/null
    # pid 文件可能是空的（上一轮已被删），再从监听端口反查一次
    if port_busy && command -v ss >/dev/null 2>&1; then
      for p in $(ss -ltnp 2>/dev/null | grep ":$PORT " | sed -n 's/.*pid=\([0-9]*\).*/\1/p'); do
        kill -9 "$p" 2>/dev/null
      done
    fi
  fi
  sleep 1
done
rm -f "$GW/gw.pid"

if port_busy; then
  if [ ! -r /proc/net/tcp ]; then
    echo "网关仍在监听 $PORT —— 杀不掉：chroot 里 /proc 没挂载，pkill 看不见进程"
  else
    echo "网关仍在监听 $PORT —— SIGKILL 也没杀掉，可能是 D 状态进程，重启手机或等它自己退"
  fi
  exit 1
fi
echo "网关已停"
exit 0
