import os

from lib import PORT, get_repl, split_result


def test_repl():
    isa_repl = get_repl(PORT)
    theory_file = os.path.abspath("python-test/Test.thy")

    ok, msg = split_result(isa_repl._initializeRepl(theory_file))
    assert ok, msg

    ok, msg = split_result(isa_repl._compile())
    assert ok, msg

    theorem = 'lemma fixes x :: int shows "x ^ 3 = x * x * x" \n proof- \n'
    ok, msg = split_result(isa_repl._step(theorem))
    assert ok, msg
    assert "1. x ^ 3 = x * x * x" in msg

    ok, msg = split_result(isa_repl._step("show ?thesis by (simp add: numeral_eq_Suc)"))
    assert ok, msg
    assert "No subgoals!" in msg


if __name__ == "__main__":
    from lib import run_jar_file
    jvm_process = run_jar_file("target/IsaREPL.jar")
    try:
        test_repl()
        print("test_repl passed")
    finally:
        jvm_process.terminate()
        jvm_process.wait()
