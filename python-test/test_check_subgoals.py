import subprocess
import time
import os
from py4j.java_gateway import JavaGateway, GatewayParameters

### subprocess to run the jar file
def run_jar_file(jar_path, port):
    process = subprocess.Popen([
        "java", "-jar", jar_path, str(port)
    ])
    # Give the server some time to start
    time.sleep(2)
    return process

### test the isapy connection
def isapy_repl(port):
    gateway = JavaGateway(gateway_parameters=GatewayParameters(port=port))
    isa_repl = gateway.entry_point
    return isa_repl

def test_proof(isa_repl):
    # Initialize REPL with a theory file
    theory_file = os.path.abspath("python-test/Test.thy")
    isa_repl._initializeRepl(theory_file)
            
    # Compile the theory file
    result = isa_repl._compile()
    print("Compilation result:", result)
            
    # Create and prove a theorem
    theorem = "lemma test: assumes \"A = True\" shows \"A \\<Longrightarrow> B \\<Longrightarrow> C \\<Longrightarrow> A \\<and> B \\<and> C\" \n"
    result = isa_repl._step(theorem)
    print("Theorem declaration result:", result)
            
    # Add a proof step
    proof_step = "apply (rule conjI)"
    result = isa_repl._step(proof_step)
    print("Proof step result:", result)

    # Check if the proof is finished
    result = isa_repl._proof_finished()
    ok, msg = result.split("<\\SEP>", 1)
    assert ok == "False"

    proof_step = "apply simp\napply simp"
    result = isa_repl._step(proof_step)
    print("Proof step result:", result)

    # Check if the proof is finished
    result = isa_repl._proof_finished()
    ok, msg = result.split("<\\SEP>", 1)
    print("Proof finished result:", result)
    assert ok == "True"


"""
Test the IsaREPL server with a sub-repl connection
    using subprocess to open the JVM server
    and py4j to connect to the server
"""
if __name__ == "__main__":
    jar_path = "target/IsaREPL.jar"
    port = 23333
    
    # Start the JVM server
    jvm_process = run_jar_file(jar_path, port)
        
    # Connect to the server
    isa_repl = isapy_repl(port)
        
    # Your test code here
    print("Successfully connected to IsaREPL server")

    test_proof(isa_repl)

    jvm_process.terminate()
    jvm_process.wait()  # Wait for process to terminate
