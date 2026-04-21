import os
import tempfile

from utils.repl import IsaRepl


def build_sample_theory(path: str):
    theory_text = """theory TraceExplicitDemo
imports Main
begin

lemma demo_simp: "(n::nat) + 0 = n"
  by simp

lemma demo_auto: "P \\<and> Q \\<Longrightarrow> Q"
  by auto

end
"""
    with open(path, "w", encoding="utf-8") as f:
        f.write(theory_text)


def test_trace_interface_and_theory_explicitization():
    output_root = os.path.abspath("logs/test_outputs")
    os.makedirs(output_root, exist_ok=True)
    tmpdir = tempfile.mkdtemp(dir=output_root, prefix="trace_explicit_")
    print(f"Outputs kept in: {tmpdir}")
    source_thy = os.path.abspath(os.path.join(tmpdir, "TraceExplicitDemo.thy"))
    build_sample_theory(source_thy)

    with IsaRepl(port=25561, create_port=True) as repl:
        stable_theory = os.path.abspath("python-test/Test.thy")
        ok, msg = repl.initialize(
            stable_theory,
            working_dir=tmpdir,
            session="HOL",
            session_dirs=[],
        )
        assert ok, msg

        ok, msg = repl.compile()
        assert ok, msg

        ok, msg = repl.step('lemma "(3::nat) <= (4::nat)"')
        assert ok, msg

        trace_ok, trace_info = repl.trace_proof_step("by simp")
        assert trace_ok, trace_info.get("error", "")
        assert trace_info["method"] == "simp"
        assert trace_info["explicit_command"].strip() != ""
        assert isinstance(trace_info["trace"]["trace_lines"], list)
        assert isinstance(trace_info["trace"]["rewrite_rules"], list)

        rewrite_result = repl.explicitize_theory_file(
            source_thy_path=source_thy,
            working_dir=tmpdir,
            session="HOL",
            session_dirs=[],
            check_output_with_compile=False,
            show_progress=True,
        )

        output_thy = rewrite_result["output_theory"]
        log_path = rewrite_result["log_file"]
        assert rewrite_result["compile_checked"] is False
        assert os.path.exists(output_thy)
        assert os.path.exists(log_path)

        with open(output_thy, "r", encoding="utf-8") as f:
            output_text = f.read()
        assert "simp only:" in output_text or "auto simp only:" in output_text

        replaced_entries = [
            item
            for item in rewrite_result["replacements"]
            if item.get("status") == "replaced"
        ]
        assert replaced_entries, "Expected at least one replaced step"
        assert any(item["statement"] in {"demo_simp", "demo_auto"} for item in replaced_entries)


if __name__ == "__main__":
    test_trace_interface_and_theory_explicitization()
    print("test_trace_interface_and_theory_explicitization passed")
