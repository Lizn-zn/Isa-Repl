import os

from lib import PORT, get_repl, split_result


def test_extract_goals():
    isa_repl = get_repl(PORT)
    theory_file = os.path.abspath("python-test/Test.thy")

    ok, msg = split_result(isa_repl._initializeRepl(theory_file))
    assert ok, msg

    ok, msg = split_result(isa_repl._compile())
    assert ok, msg

    ok, msg = split_result(isa_repl._step(r"""
      theorem example5: "\<not>(\<forall>(n::nat). f (f n) = n + 1987)"
        proof
          assume A: "\<forall> n. f (f n) = n + 1987"
          have inj_f: "inj f"
          proof (rule inj_onI)
            fix m n
            assume "f m = f n"
            have "f (f m) = f (f n)"
              using \<open>f m = f n\<close> by force
            from A
            have "f (f m) = m + 1987" and "f (f n) = n + 1987"
              by auto
    """))
    assert ok, msg

    finished, _ = split_result(isa_repl._proof_finished())
    print(finished)
    assert not finished, "proof should not yet be finished"


if __name__ == "__main__":
    from lib import run_jar_file
    jvm_process = run_jar_file("target/IsaREPL.jar")
    try:
        test_extract_goals()
        print("test_extract_goals passed")
    finally:
        jvm_process.terminate()
        jvm_process.wait()
