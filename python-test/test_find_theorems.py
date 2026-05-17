import os

from lib import PORT, get_repl, split_result


def test_find_theorems():
    isa_repl = get_repl(PORT)
    theory_file = os.path.abspath("python-test/Test.thy")

    ok, msg = split_result(isa_repl._initializeRepl(theory_file))
    assert ok, msg

    ok, msg = split_result(isa_repl._compile())
    assert ok, msg

    # Similar to: find_theorems "obj_at _ _" "set_thread_state"
    # Here we use a HOL query that should reliably return matches.
    query_patterns = ["(_::nat) <= _"]
    raw = isa_repl._find_theorems(query_patterns)
    ok, output = split_result(raw)

    print("find_theorems raw output:\n", output)
    assert ok, output
    assert "find_theorems" in output
    assert "theorem(s)" in output


if __name__ == "__main__":
    from lib import run_jar_file
    jvm_process = run_jar_file("target/IsaREPL.jar")
    try:
        test_find_theorems()
        print("test_find_theorems passed")
    finally:
        jvm_process.terminate()
        jvm_process.wait()
