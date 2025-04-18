import os
import subprocess
from py4j.java_gateway import JavaGateway, GatewayParameters
import pytest

def test_repl():
    jar_path = os.getenv("ISA_REPL_PATH")
    process = subprocess.Popen([
        "java", "-jar", jar_path, str(25555)
    ])

    # Connect to the JVM
    gateway = JavaGateway(gateway_parameters=GatewayParameters(port=25555))
            
    # Get the IsaREPL application
    isa_repl = gateway.entry_point
            
    # Initialize REPL with a theory file
    theory_file = os.path.abspath("python-test/Test.thy")

    isa_repl._initializeRepl(theory_file)
            
    # Compile the theory file
    result = isa_repl._compile()
    assert result == "True<\\SEP>"
            
    # Create and prove a theorem
    theorem = "lemma fixes x :: int shows \"x ^ 3 = x * x * x\" \n proof- \n"
    result = isa_repl._step(theorem)
    assert "1. x ^ 3 = x * x * x" in result
            
    # Add a proof step
    proof_step = "show ?thesis by (simp add: numeral_eq_Suc)"
    result = isa_repl._step(proof_step)
    assert "No subgoals!" in result
            
    # Close the REPL
    isa_repl._exit()

    # Terminate the Java process
    process.terminate()

