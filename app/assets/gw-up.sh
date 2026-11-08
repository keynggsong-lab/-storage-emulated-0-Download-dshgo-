#!/bin/bash
# 幂等启动:网关 + 看门狗。重复执行无副作用。
GW="$(cd "$(dirname "$0")" 2>/dev/null && pwd)"
[ -z "$GW" ] && GW=/root/workspace/gw
PIDF=$GW/gw.pid; WDF=$GW/watchdog.pid

# 加载 gw.env（所有开关在这里）
if [ -f "$GW/gw.env" ]; then
  set -a
  . "$GW/gw.env"
  set +a
fi
: "${GW_PORT:=7864}"

alive(){ curl -s -o /dev/null -m 2 -w '%{http_code}' "http://127.0.0.1:$GW_PORT/v1/models" 2>/dev/null | grep -qE '^[1-5][0-9][0-9]$'; }

# 端口是否还在 LISTEN。只看 /proc/net/tcp，不依赖 ss —— chroot 里 /proc 挂上就一定能用。
# 状态码 0A = TCP_LISTEN。
port_busy() {
  hex=$(printf ':%04X' "$GW_PORT")
  awk -v p="$hex" '$4=="0A" && index($2,p)>0 {found=1} END {exit !found}' \
    /proc/net/tcp /proc/net/tcp6 2>/dev/null
}

if ! alive; then
  # 关键加固：curl 不通但端口还占着 = 旧进程没死透。
  # 不清理就直接起，新进程会 EADDRINUSE 崩掉，gw.log 里只留一行错误，界面上什么都看不出来。
  if port_busy; then
    echo "端口 $GW_PORT 被残留进程占着，先清理"
    sh "$GW/gw-down.sh" >/dev/null 2>&1
    n=0
    while port_busy && [ $n -lt 8 ]; do n=$((n+1)); sleep 1; done
    if port_busy; then
      echo "警告：端口 $GW_PORT 仍被占用，这次启动大概率失败（看 gw.log）"
    fi
  fi

  nohup node $GW/proxy.js >> $GW/gw.log 2>&1 &
  echo $! > $PIDF
  sleep 1
  if alive; then
    echo "网关已启动 pid=$(cat $PIDF)"
  elif port_busy; then
    echo "网关启动异常：端口在但探测不通，看 $GW/gw.log"
  else
    echo "网关启动失败 pid=$(cat $PIDF)，看 $GW/gw.log"
  fi
else
  echo "网关已在运行"
fi

if [ -f "$WDF" ] && kill -0 "$(cat $WDF)" 2>/dev/null; then
  echo "看门狗已在运行 pid=$(cat $WDF)"
else
  nohup bash $GW/watchdog.sh >> $GW/watchdog.log 2>&1 &
  echo $! > $WDF
  echo "看门狗已启动 pid=$(cat $WDF)"
fi
sleep 1; tail -1 $GW/gw.log
