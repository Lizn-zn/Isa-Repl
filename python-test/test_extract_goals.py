import os
from py4j.java_gateway import JavaGateway, GatewayParameters
import subprocess
import time
import pytest

jar_path = os.getenv("ISA_REPL_PATH")
process = subprocess.Popen([
    "java", "-jar", jar_path, str(25555)
])
# Give the server some time to start
time.sleep(1)

# Connect to the JVM
gateway = JavaGateway(gateway_parameters=GatewayParameters(port=25555))
        
        
# Get the IsaREPL application
isa_repl = gateway.entry_point
        
# Initialize REPL with a theory file
theory_file = os.path.abspath("python-test/Test.thy")
isa_repl._initializeRepl(theory_file)
        
# Compile the theory file
result = isa_repl._compile()
print("Compilation result:", result)
        
def test_check_no_subgoals():
    isa_repl._step("""
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
    """)
    print(isa_repl._subgoal_finished())

test_check_no_subgoals()

process.terminate()
process.wait()