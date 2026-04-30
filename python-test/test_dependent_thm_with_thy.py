import os
import tempfile

from lib import L4V_PATH, PORT, THEORY_TEMPLATE, get_repl, needs_l4v, split_result


@needs_l4v
def test_dependent_thm_with_thy():
    isa_repl = get_repl(PORT)
    session = "AInvs"
    theory_name = "KHeap_AI"

    with tempfile.TemporaryDirectory(dir=".") as tmpdir:
        thy_path = os.path.join(tmpdir, "Test.thy")
        with open(thy_path, "w") as f:
            f.write(THEORY_TEMPLATE.format(session=session, theory_name=theory_name))

        ok, msg = split_result(
            isa_repl._initializeRepl(thy_path, L4V_PATH, session, [L4V_PATH])
        )
        assert ok, msg

        ok, msg = split_result(isa_repl._compile())
        assert ok, msg
        print("Compilation result:", msg)

        ok, deps = split_result(
            isa_repl._extract_thm_deps_with_thy_names("get_object_inv")
        )
        assert ok, deps
        result_lst = deps.split("<\\SEP>")
        print(result_lst[1:])
        print("length of thms:", len(result_lst))

        isa_repl._exit()


if __name__ == "__main__":
    from lib import run_jar_file
    jvm_process = run_jar_file("target/IsaREPL.jar")
    try:
        test_dependent_thm_with_thy()
        print("test_dependent_thm_with_thy passed")
    finally:
        jvm_process.terminate()
        jvm_process.wait()
