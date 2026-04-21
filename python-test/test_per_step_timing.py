"""Per-step timing for Finalise_AI.thy to see where time actually goes.

Goal: rank steps by wall time, split by tactic family (simp/auto/wp/clarsimp/…),
so we know whether simp-focused explicitization can move the needle.
"""
import os
import re
import sys
import time
from collections import defaultdict

from utils.repl import IsaRepl


WORKING_DIR = "/home/hbd/verification/l4v/"
SESSION = "AInvs"
SESSION_DIRS = ["/home/hbd/verification/l4v/"]
THY = "/home/hbd/verification/l4v/proof/invariant-abstract/Finalise_AI.thy"

OUT_DIR = os.path.abspath("logs/test_outputs/per_step_timing")


def classify(step: str) -> str:
    body = step.lstrip()
    m = re.match(r"(apply|by|using)\s*(?:\(\s*)?(\w+)", body)
    if not m:
        if body.startswith("done") or body.startswith("qed"):
            return "<close>"
        return "<other>"
    tac = m.group(2)
    # Common l4v/AInvs tactics to bucket together
    return tac


def main() -> None:
    os.makedirs(OUT_DIR, exist_ok=True)
    csv_path = os.path.join(OUT_DIR, "per_step.csv")

    with open(THY, "r", encoding="utf-8") as f:
        content = f.read()

    with IsaRepl(port=25570, create_port=True) as repl:
        repl.initialize(THY, WORKING_DIR, SESSION, SESSION_DIRS)
        ok, steps = repl.parse(content)
        if not ok:
            raise RuntimeError("parse failed")
        total = len(steps)
        print(f"Parsed {total} steps", flush=True)

        per_step: list[tuple[int, float, str, str]] = []
        t_all_start = time.perf_counter()
        executed = 0
        failed_at = -1

        for i, step in enumerate(steps):
            if not step.strip():
                continue
            tac = classify(step)
            t0 = time.perf_counter()
            ok, msg = repl.step(step)
            dt = time.perf_counter() - t0
            if not ok:
                failed_at = i
                print(
                    f"\nstep {i} failed ({tac}, {dt:.2f}s): {step[:120]!r}\n  msg: {msg[:200]}",
                    flush=True,
                )
                break
            executed += 1
            short = " ".join(step.split())[:160]
            per_step.append((i, dt, tac, short))
            if executed % 50 == 0:
                elapsed = time.perf_counter() - t_all_start
                sys.stderr.write(f"\r  {executed}/{total} steps, {elapsed:.1f}s")
                sys.stderr.flush()
        sys.stderr.write("\n")
        total_elapsed = time.perf_counter() - t_all_start

    # Write full CSV
    with open(csv_path, "w", encoding="utf-8") as f:
        f.write("index,seconds,tactic,step\n")
        for idx, dt, tac, short in per_step:
            csv_step = short.replace('"', "'")
            f.write(f'{idx},{dt:.4f},{tac},"{csv_step}"\n')

    # Per-tactic totals
    by_tac: dict[str, tuple[int, float]] = defaultdict(lambda: (0, 0.0))
    for _, dt, tac, _ in per_step:
        cnt, tot = by_tac[tac]
        by_tac[tac] = (cnt + 1, tot + dt)

    print(f"\n=== Total: {total_elapsed:.2f}s across {executed} steps ===")
    print(f"failed_at: {failed_at}")
    print(f"csv: {csv_path}")

    print("\n=== By tactic (sorted by total time) ===")
    rows = sorted(by_tac.items(), key=lambda kv: kv[1][1], reverse=True)
    print(f"{'tactic':20s} {'count':>6s} {'total_s':>10s} {'avg_s':>8s} {'pct':>6s}")
    for tac, (cnt, tot) in rows:
        pct = tot / total_elapsed * 100.0 if total_elapsed > 0 else 0.0
        avg = tot / cnt if cnt > 0 else 0.0
        print(f"{tac:20s} {cnt:>6d} {tot:>10.3f} {avg:>8.4f} {pct:>5.1f}%")

    print("\n=== Top 30 slowest steps ===")
    top = sorted(per_step, key=lambda row: row[1], reverse=True)[:30]
    for idx, dt, tac, short in top:
        print(f"  step {idx:4d}  {dt:7.3f}s  [{tac}]  {short}")


if __name__ == "__main__":
    main()
