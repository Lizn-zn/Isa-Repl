import os

from lib import PORT, get_repl, split_result


def test_hammer_facts():
    isa_repl = get_repl(PORT)
    theory_file = os.path.abspath("python-test/Test.thy")

    ok, msg = split_result(isa_repl._initializeRepl(theory_file))
    assert ok, msg

    ok, msg = split_result(isa_repl._compile())
    assert ok, msg

    theorem = 'lemma fixes x :: int shows "x ^ 3 = x * x * x" \n proof- \n'
    ok, msg = split_result(isa_repl._step(theorem))
    assert ok, msg

    ok, facts = split_result(isa_repl._extract_hammer_facts())
    assert ok, facts
    print("Extracted hammer facts:", facts)

    ok, msg = split_result(isa_repl._step("show ?thesis by (simp add: numeral_eq_Suc)"))
    assert ok, msg


if __name__ == "__main__":
    from lib import run_jar_file
    jvm_process = run_jar_file("target/IsaREPL.jar")
    try:
        test_hammer_facts()
        print("test_hammer_facts passed")
    finally:
        jvm_process.terminate()
        jvm_process.wait()
