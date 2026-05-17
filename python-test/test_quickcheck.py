import os

from lib import PORT, get_repl


def test_quickcheck():
    isa_repl = get_repl(PORT)
    theory_file = os.path.abspath("python-test/Test.thy")
    # Initialize REPL with a theory file
    isa_repl._initializeRepl(theory_file)

    # Compile the theory file
    result = isa_repl._compile()
    print("Compilation result:", result)

    # Create and prove a theorem
    theorem = 'lemma "(3::nat) <= (4::nat)"'
    result = isa_repl._step(theorem)
    print("Theorem declaration result:", result)

    # Add a proof step
    # proof_step = "show ?thesis by (simp add: numeral_eq_Suc)"
    # result = isa_repl._step(proof_step)
    # print("Proof step result:", result)
    # proof_step = "show ?thesis by (simp add: numeral_eq_Suc)"
    result = isa_repl._check_by_quickcheck()
    print("QuickCheck result:", result)

    result = isa_repl._extract_hammer_facts()
    print("Extract fact result: ", result)


"""
Test the IsaREPL server with a sub-repl connection
    using subprocess to open the JVM server
    and py4j to connect to the server
"""
if __name__ == "__main__":
    from lib import run_jar_file
    jvm_process = run_jar_file("target/IsaREPL.jar")
    try:
        test_quickcheck()
        print("test_quickcheck passed")
    finally:
        jvm_process.terminate()
        jvm_process.wait()
