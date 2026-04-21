"""Per-step timing on the explicit Finalise_AI to compare against original."""
import os
import re
import shutil
import sys
import tempfile
import time
from collections import defaultdict

from utils.repl import IsaRepl


WORKING_DIR = "/home/hbd/verification/l4v/"
SESSION = "AInvs"
SESSION_DIRS = ["/home/hbd/verification/l4v/"]
EXPLICIT_SRC = (
    "/home/hbd/projects/Isa-Repl/logs/test_outputs/"
    "trace_explicit_real_t5de07uj/Finalise_AI_explicit.thy"
)

OUT_DIR = os.path.abspath("logs/test_outputs/per_step_timing_explicit")


def classify(step: str) -> str:
    body = step.lstrip()
    m = re.match(r"(apply|by|using)\s*(?:\(\s*)?(\w+)", body)
    if not m:
        if body.startswith("done") or body.startswith("qed"):
            return "<close>"
        return "<other>"
    return m.group(2)


def stage_as_finalise_ai() -> str:
    staging = tempfile.mkdtemp(prefix="explicit_stage_")
    dst = os.path.join(staging, "Finalise_AI.thy")
    shutil.copyfile(EXPLICIT_SRC, dst)
    return dst


def main() -> None:
    os.makedirs(OUT_DIR, exist_ok=True)
    thy = stage_as_finalise_ai()

    with open(thy, "r", encoding="utf-8") as f:
        content = f.read()

    with IsaRepl(port=25571, create_port=True) as repl:
        repl.initialize(thy, WORKING_DIR, SESSION, SESSION_DIRS)
        ok, steps = repl.parse(content)
        if not ok:
            raise RuntimeError("parse failed")

        per_step: list[tuple[int, float, str, str]] = []
        t_all = time.perf_counter()
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
                print(f"\nstep {i} failed: {step[:120]!r}\n  msg: {msg[:200]}")
                break
            executed += 1
            short = " ".join(step.split())[:160]
            per_step.append((i, dt, tac, short))
            if executed % 50 == 0:
                sys.stderr.write(f"\r  {executed}, {time.perf_counter()-t_all:.1f}s")
                sys.stderr.flush()
        sys.stderr.write("\n")
        total = time.perf_counter() - t_all

    # Per-tactic totals
    by_tac: dict[str, tuple[int, float]] = defaultdict(lambda: (0, 0.0))
    for _, dt, tac, _ in per_step:
        cnt, tot = by_tac[tac]
        by_tac[tac] = (cnt + 1, tot + dt)

    print(f"\n=== Explicit total: {total:.2f}s across {executed} steps ===")
    print(f"{'tactic':20s} {'count':>6s} {'total_s':>10s} {'avg_s':>8s} {'pct':>6s}")
    for tac, (cnt, tot) in sorted(by_tac.items(), key=lambda kv: kv[1][1], reverse=True):
        pct = tot / total * 100.0 if total > 0 else 0.0
        avg = tot / cnt if cnt > 0 else 0.0
        print(f"{tac:20s} {cnt:>6d} {tot:>10.3f} {avg:>8.4f} {pct:>5.1f}%")

    # Spotlight: the two `only:` replacement steps (indices 269 and 526 in original)
    print("\n=== Spotlight: only:-form replacements ===")
    for idx, dt, tac, short in per_step:
        if "only:" in short:
            print(f"  step {idx:4d}  {dt:7.3f}s  {short[:150]}")


if __name__ == "__main__":
    main()
