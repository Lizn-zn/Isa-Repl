import os

from lib import PORT, get_repl, split_result


def test_smt():
    isa_repl = get_repl(PORT)
    theory_file = os.path.abspath("python-test/Test.thy")

    ok, msg = split_result(isa_repl._initializeRepl(theory_file))
    assert ok, msg

    ok, msg = split_result(isa_repl._compile())
    assert ok, msg

    theorem = 'lemma fixes x :: int shows "x ^ 3 = x * x * x" \n proof- \n'
    ok, msg = split_result(isa_repl._step(theorem))
    assert ok, msg

    result = isa_repl._translate_to_smt()
    print("SMT translation result:", result)


if __name__ == "__main__":
    from lib import run_jar_file
    jvm_process = run_jar_file("target/IsaREPL.jar")
    try:
        test_smt()
        print("test_smt passed")
    finally:
        jvm_process.terminate()
        jvm_process.wait()
