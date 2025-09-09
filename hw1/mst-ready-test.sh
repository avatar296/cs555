#!/usr/bin/env bash
set -euo pipefail

# mst-ready-test.sh — End-to-end stress of "weightsReady wait" + MST determinism on ALL nodes.

# --------------------------- CONFIG --------------------------------
CLASSPATH_DEFAULT="build/classes/java/main"
REG_CMD_DEFAULT="java -Djava.net.preferIPv4Stack=true -cp ${CLASSPATH_DEFAULT} csx55.overlay.node.Registry"
NODE_CMD_DEFAULT="java -Djava.net.preferIPv4Stack=true -cp ${CLASSPATH_DEFAULT} csx55.overlay.node.MessagingNode"

REG_CMD="${REG_CMD:-$REG_CMD_DEFAULT}"
NODE_CMD="${NODE_CMD:-$NODE_CMD_DEFAULT}"

NODES="${NODES:-12}"
REG_PORT="${REG_PORT:-8080}"
CR="${CR:-4}"

RUN_DIR="${RUN_DIR:-.hw1-readytest}"
TOOLS_DIR="$RUN_DIR/tools"
WEIGHTS_FILE="$RUN_DIR/weights.txt"

# How aggressively we hammer 'print-mst' right after weights:
PRINT_AFTER_SEND_DELAY="${PRINT_AFTER_SEND_DELAY:-0.05}"   # seconds
COLLECT_TIMEOUT="${COLLECT_TIMEOUT:-8}"                    # seconds
# -------------------------------------------------------------------

die(){ echo "ERROR: $*" >&2; exit 1; }
ensure_dirs(){ mkdir -p "$RUN_DIR" "$TOOLS_DIR" "$TOOLS_DIR/classes"; }
fifo(){ echo "$RUN_DIR/$1.fifo"; }
logf(){ echo "$RUN_DIR/$1.log"; }
pidf(){ echo "$RUN_DIR/$1.pid"; }
pname(){ [[ "$1" -eq 0 ]] && echo "registry" || echo "node-$1"; }

start_proc(){
  local name="$1" cmd="$2"
  local f; f="$(fifo "$name")"
  local l; l="$(logf "$name")"
  local p; p="$(pidf "$name")"
  rm -f "$f"; mkfifo "$f"
  : > "$l"
  ( set -o pipefail
    tail -f "$f" | bash -lc "$cmd" >>"$l" 2>&1
  ) &
  echo $! > "$p"
  sleep 0.25
}

send(){ local n="$1"; shift; printf '%s\n' "$*" > "$(fifo "$n")"; }

lines_since(){ awk -v from="$2" 'NR>from{print}' "$(logf "$1")" 2>/dev/null || true; }
lc(){ [[ -f "$(logf "$1")" ]] && wc -l <"$(logf "$1")" || echo 0; }

compile_helpers(){
  ensure_dirs
  cat > "$TOOLS_DIR/MSTWeightChecker.java" <<'JAVA'
package tools;
import java.io.*; import java.util.*;
public class MSTWeightChecker {
  static class DSU{ Map<String,String>p=new HashMap<>();Map<String,Integer>r=new HashMap<>();
    String f(String x){ p.putIfAbsent(x,x); if(!p.get(x).equals(x)) p.put(x,f(p.get(x))); return p.get(x); }
    boolean u(String a,String b){ a=f(a); b=f(b); if(a.equals(b))return false; int ra=r.getOrDefault(a,0), rb=r.getOrDefault(b,0);
      if(ra<rb)p.put(a,b); else if(rb<ra)p.put(b,a); else{p.put(b,a); r.put(a,ra+1);} return true;}}
  static class E{ String a,b; int w; }
  public static void main(String[] args)throws Exception{
    BufferedReader br=new BufferedReader(new InputStreamReader(System.in));
    List<E> edges=new ArrayList<>(); Set<String> nodes=new HashSet<>(); String line;
    while((line=br.readLine())!=null){ line=line.trim(); if(line.isEmpty())continue;
      String[] parts=line.split(","); if(parts.length!=3)continue;
      String a=parts[0].trim(), b=parts[1].trim(); int w=Integer.parseInt(parts[2].trim());
      E e=new E(); e.a=a; e.b=b; e.w=w; edges.add(e); nodes.add(a); nodes.add(b); }
    edges.sort(Comparator.comparingInt(e->e.w));
    DSU d=new DSU(); int total=0,picked=0; int need=Math.max(0,nodes.size()-1);
    for(E e:edges){ if(d.u(e.a,e.b)){ total+=e.w; picked++; if(picked==need)break; } }
    System.out.println(total);
  }
}
JAVA

  cat > "$TOOLS_DIR/NodeMSTPrinter.java" <<'JAVA'
package tools;
import csx55.overlay.spanning.Edge;
import csx55.overlay.spanning.MinimumSpanningTree;
import java.io.*; import java.util.*;
public class NodeMSTPrinter {
  static String key(String a,String b){ a=a.trim(); b=b.trim(); return (a.compareTo(b)<=0)?a+"||"+b:b+"||"+a; }
  public static void main(String[] args)throws Exception{
    BufferedReader br=(args.length>0 && !args[0].isEmpty())
        ? new BufferedReader(new FileReader(args[0]))
        : new BufferedReader(new InputStreamReader(System.in));
    LinkedHashSet<String> order=new LinkedHashSet<>();
    Map<String,Integer> best=new HashMap<>();
    String line; while((line=br.readLine())!=null){ line=line.trim(); if(line.isEmpty())continue;
      String[] parts=line.split(","); if(parts.length!=3)continue;
      String a=parts[0].trim(), b=parts[1].trim(); if(a.equals(b))continue;
      int w=Integer.parseInt(parts[2].trim()); order.add(a); order.add(b);
      String k=key(a,b); best.put(k, Math.min(best.getOrDefault(k, Integer.MAX_VALUE), w)); }
    if(order.isEmpty()){ System.err.println("no edges"); return; }
    Map<String,List<Edge>> g=new HashMap<>(); for(String n:order) g.put(n,new ArrayList<>());
    for(Map.Entry<String,Integer> en:best.entrySet()){ String[] ab=en.getKey().split("\\|\\|",2);
      String a=ab[0], b=ab[1]; int w=en.getValue(); g.get(a).add(new Edge(b,w)); g.get(b).add(new Edge(a,w)); }
    String root=order.iterator().next();
    MinimumSpanningTree mst=new MinimumSpanningTree(root,g);
    if(!mst.calculate()){ System.out.println("MST has not been calculated yet."); return; }
    for(String e: mst.getFormattedEdges()) System.out.println(e);
    System.out.println("TOTAL: " + mst.getTotalWeight());
  }
}
JAVA

  javac -cp "$CLASSPATH_DEFAULT" -d "$TOOLS_DIR/classes" "$TOOLS_DIR/MSTWeightChecker.java" "$TOOLS_DIR/NodeMSTPrinter.java"
}

start_all(){
  ensure_dirs
  echo ">>> Starting registry :$REG_PORT"
  start_proc "registry" "$REG_CMD $REG_PORT"
  for i in $(seq 1 "$NODES"); do
    echo ">>> Starting node-$i"
    start_proc "node-$i" "$NODE_CMD localhost $REG_PORT"
  done
  echo ">>> Waiting for $NODES registrations..."
  local tries=60
  while ((tries-- > 0)); do
    local before; before="$(lc registry)"
    send 0 "list-messaging-nodes"
    sleep 0.6
    local got; got="$(lines_since registry "$before" | grep -E '^[^[:space:]]+:[0-9]+$' | wc -l | tr -d ' ')"
    [[ "$got" -eq "$NODES" ]] && { echo ">>> All nodes registered."; return; }
  done
  echo ">>> Proceeding despite partial registration."
}

setup_overlay(){
  echo ">>> setup-overlay CR=$CR"
  send 0 "setup-overlay $CR"
  sleep 0.7
  echo ">>> send-overlay-link-weights"
  send 0 "send-overlay-link-weights"
  # hammer print-mst almost immediately to test weightsReady wait
  sleep "$PRINT_AFTER_SEND_DELAY"
  for i in $(seq 1 "$NODES"); do
    send "$i" "print-mst" &
  done
}

capture_weights_and_expected(){
  local before; before="$(lc registry)"
  send 0 "list-weights"
  sleep 0.8
  lines_since registry "$before" | grep -E '^[^,]+:[0-9]+, [^,]+:[0-9]+, [0-9]+' > "$WEIGHTS_FILE" || true
  if [[ ! -s "$WEIGHTS_FILE" ]]; then die "list-weights produced no lines"; fi
  echo ">>> list-weights lines: $(wc -l < "$WEIGHTS_FILE" | tr -d ' ')"
  echo -n ">>> EXPECTED (Kruskal) total: "
  EXPECTED="$(java -cp "$TOOLS_DIR/classes" tools.MSTWeightChecker < "$WEIGHTS_FILE")"
  echo "$EXPECTED"
}

collect_node_totals(){
  echo ">>> Collecting print-mst totals from all nodes (timeout ${COLLECT_TIMEOUT}s)..."
  local start_ts; start_ts=$(date +%s)
  declare -gA NODE_TOTALS=()
  declare -gA EDGE_COUNTS=()
  local i
  for i in $(seq 1 "$NODES"); do NODE_TOTALS["$i"]=""; EDGE_COUNTS["$i"]=""; done

  while :; do
    local all_done=1
    for i in $(seq 1 "$NODES"); do
      if [[ -z "${NODE_TOTALS[$i]}" ]]; then
        # parse node-i log for "A:port, B:port, w" lines since script start
        local edges sum
        edges="$(grep -E '^[^,]+:[0-9]+, [^,]+:[0-9]+, [0-9]+' "$(logf "node-$i")" 2>/dev/null || true)"
        if [[ -n "$edges" ]]; then
          EDGE_COUNTS["$i"]="$(printf "%s\n" "$edges" | wc -l | tr -d ' ')"
          sum="$(printf "%s\n" "$edges" | awk -F',' '{gsub(/^[ \t]+|[ \t]+$/,"",$3); s+=$3} END{print s+0}')"
          NODE_TOTALS["$i"]="$sum"
        else
          all_done=0
        fi
      fi
    done
    if (( all_done )); then break; fi
    if (( $(date +%s) - start_ts >= COLLECT_TIMEOUT )); then
      echo ">>> Timeout collecting some nodes; proceeding with what we have."
      break
    fi
    sleep 0.3
  done

  echo ">>> Node totals:"
  for i in $(seq 1 "$NODES"); do
    printf "    node-%d: edges=%s total=%s\n" "$i" "${EDGE_COUNTS[$i]:-0}" "${NODE_TOTALS[$i]:-MISSING}"
  done
}

run_node_replay_check(){
  echo ">>> Replaying SAME weights via NodeMSTPrinter (deterministic):"
  java -cp "$CLASSPATH_DEFAULT:$TOOLS_DIR/classes" tools.NodeMSTPrinter "$WEIGHTS_FILE" | tail -1 | awk '{print $2}'
}

stop_all(){
  echo ">>> Stopping processes..."
  for i in 0 $(seq 1 "$NODES"); do
    local n; n="$(pname "$i")"
    local p; p="$(pidf "$n")"
    if [[ -f "$p" ]]; then
      local pid; pid="$(cat "$p" || true)"
      [[ -n "${pid:-}" ]] && kill "$pid" 2>/dev/null || true
    fi
  done
  rm -rf "$RUN_DIR"
  echo ">>> Cleaned $RUN_DIR"
}

main(){
  compile_helpers
  start_all
  setup_overlay
  sleep 2
  capture_weights_and_expected
  collect_node_totals
  
  echo ""
  echo "================================================================"
  echo "RESULTS:"
  echo "  Expected MST Total: $EXPECTED"
  local matches=0 mismatches=0
  for i in $(seq 1 "$NODES"); do
    local t="${NODE_TOTALS[$i]}"
    if [[ "$t" == "$EXPECTED" ]]; then
      ((matches++))
    else
      ((mismatches++))
      echo "  node-$i MISMATCH: got $t"
    fi
  done
  echo "  Matching nodes: $matches/$NODES"
  echo "  Mismatched nodes: $mismatches/$NODES"
  
  local replay; replay="$(run_node_replay_check)"
  echo "  NodeMSTPrinter replay: $replay"
  echo "================================================================"
  
  stop_all
  
  if (( mismatches > 0 )); then
    echo "FAIL: Some nodes had incorrect MST totals"
    exit 1
  else
    echo "PASS: All nodes computed correct MST totals!"
    exit 0
  fi
}

main "$@"