import os
from py4j.java_gateway import JavaGateway, GatewayParameters, GatewayClient
from py4j.java_collections import ListConverter

# Connect to the JVM
gateway = JavaGateway(gateway_parameters=GatewayParameters(port=25555, auto_convert=True))

# Get the IsaREPL application
isa_repl = gateway.entry_point

# Initialize REPL with a theory file
theory_file = os.path.abspath("python-test/Test.thy")


isa_repl._initializeRepl(theory_file)

# Compile the theory file
result = isa_repl._compile()
print("Compilation result:", result)

# Create and prove a theorem
theorem = "lemma fixes x :: int shows \"x ^ 3 = x * x * x\" \n proof- \n"
result = isa_repl._step(theorem)
print("Theorem declaration result:", result)

# Show which facts sledgehammer selects (currently only show "mepo" results)
result = isa_repl._extract_hammer_facts()
print("Extract fact result", result)

# Add a proof step
proof_step = "show ?thesis by (simp add: numeral_eq_Suc)"
result = isa_repl._step(proof_step)
print("Proof step result:", result)

