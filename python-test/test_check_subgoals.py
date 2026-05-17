import os

from lib import PORT, get_repl, split_result


def test_check_subgoals():
    isa_repl = get_repl(PORT)
    theory_file = os.path.abspath("python-test/Test.thy")

    ok, msg = split_result(isa_repl._initializeRepl(theory_file))
    assert ok, msg

    ok, msg = split_result(isa_repl._compile())
    assert ok, msg

    theorem = 'lemma test: assumes "A = True" shows "A \\<Longrightarrow> B \\<Longrightarrow> C \\<Longrightarrow> A \\<and> B \\<and> C" \n'
    ok, msg = split_result(isa_repl._step(theorem))
    assert ok, msg

    ok, msg = split_result(isa_repl._step("apply (rule conjI)"))
    assert ok, msg

    finished, _ = split_result(isa_repl._proof_finished())
    assert not finished, "proof should not yet be finished"

    ok, msg = split_result(isa_repl._step("apply simp\napply simp"))
    assert ok, msg

    finished, _ = split_result(isa_repl._proof_finished())
    assert finished, "proof should now be finished"


if __name__ == "__main__":
    from lib import run_jar_file
    jvm_process = run_jar_file("target/IsaREPL.jar")
    try:
        test_check_subgoals()
        print("test_check_subgoals passed")
    finally:
        jvm_process.terminate()
        jvm_process.wait()
