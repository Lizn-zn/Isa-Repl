import os
import shutil
import sys
import tempfile
import time

from utils.repl import IsaRepl


WORKING_DIR = "/home/hbd/verification/l4v/"
SESSION = "AInvs"
SESSION_DIRS = ["/home/hbd/verification/l4v/"]

ORIGINAL_THY = "/home/hbd/verification/l4v/proof/invariant-abstract/Finalise_AI.thy"
EXPLICIT_SRC = (
    "/home/hbd/projects/Isa-Repl/logs/test_outputs/"
    "trace_explicit_real_6jki_nut/Finalise_AI_explicit.thy"
)


def _stage_explicit_as_original_name() -> str:
    """Isabelle requires theory name == filename. Copy explicit thy to Finalise_AI.thy."""
    staging = tempfile.mkdtemp(prefix="explicit_stage_")
    dst = os.path.join(staging, "Finalise_AI.thy")
    shutil.copyfile(EXPLICIT_SRC, dst)
    return dst


def replay_and_time(repl: IsaRepl, thy_path: str, label: str) -> tuple[float, int, int]:
    with open(thy_path, "r", encoding="utf-8") as f:
        content = f.read()
    repl.initialize(thy_path, WORKING_DIR, SESSION, SESSION_DIRS)
    ok, steps = repl.parse(content)
    if not ok:
        raise RuntimeError(f"parse failed for {thy_path}")
    total = len(steps)

    t0 = time.perf_counter()
    executed = 0
    failed_at = -1
    fail_msg = ""
    for i, step in enumerate(steps):
        if not step.strip():
            continue
        ok, msg = repl.step(step)
        if not ok:
            failed_at = i
            fail_msg = msg
            break
        executed += 1
        if executed % 50 == 0:
            elapsed = time.perf_counter() - t0
            sys.stderr.write(
                f"\r  [{label}] {executed}/{total} steps, {elapsed:.1f}s"
            )
            sys.stderr.flush()
    elapsed = time.perf_counter() - t0
    sys.stderr.write("\n")

    print(f"{label}: {elapsed:.2f}s for {executed}/{total} steps", flush=True)
    if failed_at >= 0:
        short = " ".join(steps[failed_at].split())[:180]
        print(f"  failed at step {failed_at}: {short}", flush=True)
        print(f"  isabelle msg: {fail_msg[:200]}", flush=True)
    return elapsed, executed, failed_at


def main():
    print("=== Original ===", flush=True)
    with IsaRepl(port=25565, create_port=True) as repl:
        orig_elapsed, orig_exec, orig_fail = replay_and_time(repl, ORIGINAL_THY, "orig")

    explicit_thy = _stage_explicit_as_original_name()
    print(f"\n=== Explicit ({explicit_thy}) ===", flush=True)
    with IsaRepl(port=25566, create_port=True) as repl:
        expl_elapsed, expl_exec, expl_fail = replay_and_time(repl, explicit_thy, "expl")

    print("\n=== Summary ===")
    print(f"original: {orig_elapsed:.2f}s  ({orig_exec} steps executed)")
    print(f"explicit: {expl_elapsed:.2f}s  ({expl_exec} steps executed)")
    if orig_fail < 0 and expl_fail < 0:
        delta = expl_elapsed - orig_elapsed
        pct = (delta / orig_elapsed) * 100.0
        print(f"delta (explicit - original): {delta:+.2f}s  ({pct:+.1f}%)")
    else:
        print("(at least one run failed; times only comparable for the portion completed)")


if __name__ == "__main__":
    main()
