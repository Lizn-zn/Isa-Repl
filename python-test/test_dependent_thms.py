import os
from py4j.java_gateway import JavaGateway, GatewayParameters
from py4j.java_collections import ListConverter

# Connect to the JVM
gateway = JavaGateway(gateway_parameters=GatewayParameters(port=25555, auto_convert=True))

# Get the IsaREPL application
isa_repl = gateway.entry_point
        
# Initialize REPL with a theory file
theory_file = os.path.abspath("Test_Dep.thy")


isa_repl._initializeRepl(theory_file, "AInvs", ["/home/hbd/verification/l4v"], "/home/hbd/verification/l4v")
        
# Compile the theory file
result = isa_repl._compile()
print("Compilation result:", result)
        
result = isa_repl._extract_thm_deps("get_object_inv")
print("Dependent result:", result)
        
