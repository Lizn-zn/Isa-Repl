import os

from lib import PORT, get_repl


def test_sub_repl():
    isa_repl = get_repl(PORT)
    theory_file = os.path.abspath("python-test/Test.thy")
    # Initialize REPL with a theory file
    isa_repl._initializeRepl(theory_file)

    # Compile the theory file
    result = isa_repl._compile()
    print("Compilation result:", result)

    # Create and prove a theorem
    theorem = 'lemma fixes x :: int shows "x ^ 3 = x * x * x" \n proof- \n'
    result = isa_repl._step(theorem)
    print("Theorem declaration result:", result)

    # Add a proof step
    proof_step = "show ?thesis by (simp add: numeral_eq_Suc)"
    result = isa_repl._step(proof_step)
    print("Proof step result:", result)


"""
Test the IsaREPL server with a sub-repl connection
    using subprocess to open the JVM server
    and py4j to connect to the server
"""
if __name__ == "__main__":
    from lib import run_jar_file
    jvm_process = run_jar_file("target/IsaREPL.jar")
    try:
        test_sub_repl()
        print("test_sub_repl passed")
    finally:
        jvm_process.terminate()
        jvm_process.wait()
