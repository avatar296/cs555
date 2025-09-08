#!/usr/bin/env bash
set -euo pipefail

# hw-test.sh — End-to-end harness for CSx55 HW1 (NO Docker).
# - Starts Registry + N MessagingNodes as background processes (stdin piped via FIFOs)
# - Sets up overlay, sends weights, verifies counts, runs rounds
# - "all" now ALWAYS runs the full lifecycle and prints a final summary (doesn't abort early)

# --------------------------- CONFIG --------------------------------
CLASSPATH_DEFAULT="build/classes/java/main"
REG_CMD_DEFAULT="java -Djava.net.preferIPv4Stack=true -cp ${CLASSPATH_DEFAULT} csx55.overlay.node.Registry"
NODE_CMD_DEFAULT="java -Djava.net.preferIPv4Stack=true -cp ${CLASSPATH_DEFAULT} csx55.overlay.node.MessagingNode"

REG_CMD="${REG_CMD:-$REG_CMD_DEFAULT}"
NODE_CMD="${NODE_CMD:-$NODE_CMD_DEFAULT}"

NODES="${NODES:-12}"
REG_PORT="${REG_PORT:-8080}"  # default to 8080 per your last run
CR="${CR:-4}"
ROUNDS="${ROUNDS:-1}"

WAIT_AFTER_SEND_WEIGHTS="${WAIT_AFTER_SEND_WEIGHTS:-0.5}"
MST_CHECK_RETRIES="${MST_CHECK_RETRIES:-6}"
MST_CHECK_INTERVAL="${MST_CHECK_INTERVAL:-0.8}"

RUN_DIR="${RUN_DIR:-.hw1-local-run}"
# -------------------------------------------------------------------

usage() {
  cat <<EOF
Usage: $0 [command]

Commands:
  start            Start registry + N nodes (background)
  setup            setup-overlay CR + send-overlay-link-weights
  check-weights    Verify list-weights == (CR*N)/2
  check-mst        Verify each node print-mst == (N-1) edges
  start-rounds     Run messaging rounds
  logs             Tail registry + node logs (Ctrl-C to stop)
  all              Full lifecycle (start -> setup -> checks -> rounds) and final summary
  stop             Kill all processes and clean state

Environment:
  NODES=${NODES} REG_PORT=${REG_PORT} CR=${CR} ROUNDS=${ROUNDS}
  REG_CMD="${REG_CMD_DEFAULT}"
  NODE_CMD="${NODE_CMD_DEFAULT}"
EOF
}

die(){ echo "ERROR: $*" >&2; exit 1; }
ensure_run_dir(){ mkdir -p "$RUN_DIR"; }
proc_name(){ [[ "$1" -eq 0 ]] && echo "registry" || echo "node-$1"; }
fifo_path(){ echo "$RUN_DIR/$1.fifo"; }
log_path(){ echo "$RUN_DIR/$1.log"; }
pid_path(){ echo "$RUN_DIR/$1.pid"; }

start_proc(){
  local name="$1"; local cmd="$2"
  local fifo; fifo="$(fifo_path "$name")"
  local log;  log="$(log_path "$name")"
  local pidf; pidf="$(pid_path "$name")"
  rm -f "$fifo"; mkfifo "$fifo"
  : > "$log"

  ( set -o pipefail
    # tail -f the FIFO into the process; append both stdout/stderr to log
    tail -f "$fifo" | bash -lc "$cmd" >>"$log" 2>&1
  ) &
  echo $! > "$pidf"
  sleep 0.25
}

send_cmd(){ local name="$1"; shift; printf '%s\n' "$*" > "$(fifo_path "$name")"; }
linecount(){ [[ -f "$(log_path "$1")" ]] && wc -l < "$(log_path "$1")" || echo 0; }
lines_since(){ awk -v from="$2" 'NR>from{print}' "$(log_path "$1")" 2>/dev/null || true; }

start_all(){
  ensure_run_dir
  echo ">>> Starting registry on port $REG_PORT"
  start_proc "registry" "$REG_CMD $REG_PORT"

  for i in $(seq 1 "$NODES"); do
    echo ">>> Starting node-$i (registry localhost:$REG_PORT)"
    start_proc "node-$i" "$NODE_CMD localhost $REG_PORT"
  done

  echo ">>> Waiting for $NODES nodes to register..."
  local attempts=60
  while (( attempts-- > 0 )); do
    local before; before="$(linecount registry)"
    send_cmd registry "list-messaging-nodes"
    sleep 0.8
    local recent; recent="$(lines_since registry "$before")"
    # Show what the registry actually printed (helps with format issues)
    echo ">>> Registry returned:"
    echo "$recent" | sed 's/^/    /'
    # Count lines of the form "<host>:<port>" (host can be ipv4 or hostname)
    local listed
    listed="$(echo "$recent" | grep -E '^[^[:space:]]+:[0-9]+$' | wc -l | tr -d ' ')"
    if [[ "$listed" -eq "$NODES" ]]; then
      echo ">>> All $NODES nodes registered."
      return 0
    fi
  done
  echo ">>> Timeout waiting for full registration. Proceeding anyway."
  return 1
}

setup_overlay(){
  echo ">>> Setting up overlay (CR=$CR)"
  send_cmd registry "setup-overlay $CR"
  sleep 0.8
  echo ">>> Sending overlay link weights"
  send_cmd registry "send-overlay-link-weights"
  sleep 0.8
}

check_list_weights(){
  local expected=$(( (CR * NODES) / 2 ))
  local before; before="$(linecount registry)"
  send_cmd registry "list-weights"
  sleep 0.9
  # Count lines like "A:port, B:port, weight"
  local got
  got="$(lines_since registry "$before" | grep -E '^[^,]+:([0-9]+), [^,]+:([0-9]+), [0-9]+$' | wc -l | tr -d ' ')"
  echo ">>> list-weights produced $got lines; expected $expected"
  [[ "$got" -eq "$expected" ]]
}

check_node_mst(){
  local name="$1"; local expected=$(( NODES - 1 ))
  local before; before="$(linecount "$name")"
  send_cmd "$name" "print-mst"
  sleep "$WAIT_AFTER_SEND_WEIGHTS"

  local i=0 edges=0
  while (( i < MST_CHECK_RETRIES )); do
    edges="$(lines_since "$name" "$before" | grep -E '^[^,]+:([0-9]+), [^,]+:([0-9]+), [0-9]+$' | wc -l | tr -d ' ')"
    if [[ "$edges" -eq "$expected" ]]; then
      echo "[$name] MST edges OK: $edges"
      return 0
    fi
    sleep "$MST_CHECK_INTERVAL"; (( i++ ))
  done
  echo "[$name] MST edges WRONG: got $edges expected $expected"
  return 1
}

check_mst_all(){
  local ok=0 fail=0
  for i in $(seq 1 "$NODES"); do
    local name="node-$i"
    if check_node_mst "$name"; then ((ok++)); else ((fail++)); fi
  done
  echo ">>> MST summary: OK=$ok FAIL=$fail"
  # Return 0 if all pass, 1 otherwise
  (( fail == 0 ))
}

start_rounds(){
  echo ">>> Starting messaging rounds ($ROUNDS)"
  local before; before="$(linecount registry)"
  send_cmd registry "start $ROUNDS"
  # Wait a bit; then show orchestration/complete lines
  sleep 2
  lines_since registry "$before" | grep -E "TaskOrchestration|rounds completed|Received task complete" || true
}

logs(){
  echo ">>> Tailing logs (Ctrl-C to stop) ..."
  tail -n +1 -F "$RUN_DIR"/registry.log "$RUN_DIR"/node-*.log 2>/dev/null | sed -u 's/^/[LOG] /'
}

stop_all(){
  echo ">>> Stopping processes..."
  for i in 0 $(seq 1 "$NODES"); do
    local name; name="$(proc_name "$i")"
    local pidf; pidf="$(pid_path "$name")"
    if [[ -f "$pidf" ]]; then
      local pid; pid="$(cat "$pidf" || true)"
      [[ -n "${pid:-}" ]] && kill "$pid" 2>/dev/null || true
    fi
  done
  rm -rf "$RUN_DIR"
  echo ">>> Cleaned $RUN_DIR"
}

all_sequence(){
  local reg_ok=0 weights_ok=0 mst_ok=0

  # Don't let -e abort the script on a failed check
  set +e
  start_all; reg_ok=$?
  setup_overlay
  check_list_weights; weights_ok=$?
  check_mst_all; mst_ok=$?
  start_rounds
  set -e

  echo "----------------------------------------------------------------"
  echo "FINAL SUMMARY:"
  echo "  Registered all nodes:    $([[ $reg_ok -eq 0 ]] && echo OK || echo FAIL)"
  echo "  list-weights check:      $([[ $weights_ok -eq 0 ]] && echo OK || echo FAIL)"
  echo "  print-mst (all nodes):   $([[ $mst_ok -eq 0 ]] && echo OK || echo FAIL)"
  echo "----------------------------------------------------------------"

  # Exit non-zero if any critical check failed
  if (( reg_ok != 0 || weights_ok != 0 || mst_ok != 0 )); then
    exit 2
  fi
}

cmd="${1:-help}"
case "$cmd" in
  start) start_all ;;
  setup) setup_overlay ;;
  check-weights) check_list_weights ;;
  check-mst) check_mst_all ;;
  start-rounds) start_rounds ;;
  logs) logs ;;
  all) all_sequence ;;
  stop) stop_all ;;
  *) usage; exit 1 ;;
esac
