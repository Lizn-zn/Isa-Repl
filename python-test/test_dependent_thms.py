import os
from lib import get_repl, split_result, PORT, L4V_PATH, needs_l4v


@needs_l4v
def test_dependent_thms():
    isa_repl = get_repl(PORT)
    theory_file = os.path.abspath("python-test/Test_Dep.thy")

    ok, msg = split_result(
        isa_repl._initializeRepl(theory_file, L4V_PATH, "AInvs", [L4V_PATH])
    )
    assert ok, msg

    ok, msg = split_result(isa_repl._compile())
    assert ok, msg
    print("Compilation result:", msg)

    ok, deps = split_result(isa_repl._extract_thm_deps("get_object_inv"))
    assert ok, deps
    print("Dependent result:", deps)


if __name__ == "__main__":
    from lib import run_jar_file
    jvm_process = run_jar_file("target/IsaREPL.jar")
    try:
        test_dependent_thms()
        print("test_dependent_thms passed")
    finally:
        jvm_process.terminate()
        jvm_process.wait()
