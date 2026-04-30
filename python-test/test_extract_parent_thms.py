import os
import tempfile

from lib import L4V_PATH, PORT, THEORY_TEMPLATE, get_repl, needs_l4v, split_result


@needs_l4v
def test_extract_parent_thms():
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

        ok, thms = split_result(isa_repl._extract_thms_defined_in_parent())
        assert ok, thms
        result_lst = thms.split("<\\SEP>")
        print(result_lst[:10])
        print("length of thms:", len(result_lst))

        isa_repl._exit()


if __name__ == "__main__":
    from lib import run_jar_file
    jvm_process = run_jar_file("target/IsaREPL.jar")
    try:
        test_extract_parent_thms()
        print("test_extract_parent_thms passed")
    finally:
        jvm_process.terminate()
        jvm_process.wait()
