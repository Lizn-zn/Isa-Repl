import os
import tempfile

from utils.repl import IsaRepl


def test_explicitize_real_theory():
    working_dir = "/home/hbd/verification/l4v/"
    session_dirs = ["/home/hbd/verification/l4v/"]
    session = "AInvs"
    # source_thy = "/home/hbd/verification/l4v/proof/invariant-abstract/Deterministic_AI.thy"
    source_thy = "/home/hbd/verification/l4v/proof/invariant-abstract/Finalise_AI.thy"

    output_root = os.path.abspath("logs/test_outputs")
    os.makedirs(output_root, exist_ok=True)
    output_dir = tempfile.mkdtemp(dir=output_root, prefix="trace_explicit_real_")
    print(f"Outputs kept in: {output_dir}")

    source_name = os.path.basename(source_thy)
    stem, ext = os.path.splitext(source_name)
    output_thy = os.path.join(output_dir, f"{stem}_explicit{ext}")
    log_path = f"{output_thy}.explicitize.log"

    with IsaRepl(port=25562, create_port=True) as repl:
        rewrite_result = repl.explicitize_theory_file(
            source_thy_path=source_thy,
            output_thy_path=output_thy,
            log_path=log_path,
            working_dir=working_dir,
            session=session,
            session_dirs=session_dirs,
            check_output_with_compile=False,
            show_progress=True,
        )

        assert os.path.exists(rewrite_result["output_theory"])
        assert os.path.exists(rewrite_result["log_file"])

        replacements = rewrite_result["replacements"]
        replaced = sum(1 for r in replacements if r.get("status") == "replaced")
        skipped = sum(1 for r in replacements if r.get("status") == "skipped")
        print(
            f"Real theory rewrite summary: replaced={replaced}, "
            f"skipped={skipped}, total_candidates={len(replacements)}"
        )


if __name__ == "__main__":
    test_explicitize_real_theory()
    print("test_explicitize_real_theory passed")
