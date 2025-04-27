import os
import tempfile
from py4j.java_gateway import JavaGateway, GatewayParameters

# Connect to the JVM
gateway = JavaGateway(gateway_parameters=GatewayParameters(port=25555, auto_convert=True))
# Get the IsaREPL application
isa_repl = gateway.entry_point

# Initialize REPL with a theory file
template = """
theory Test
    imports {logic}.{theory_name}
begin
"""


def get_thms(logic, theory_name):
    with tempfile.TemporaryDirectory(dir='.') as tmpdirname:
        print("tempdir:", tmpdirname)
        thy_path = os.path.join(tmpdirname, 'Test.thy')
        with open(thy_path, 'w') as f:
            f.write(template.format(logic=logic, theory_name=theory_name))
        print(f.name)
        isa_repl._initializeRepl(thy_path, "/home/hbd/verification/l4v", logic, ["/home/hbd/verification/l4v"])

        # Compile the theory file
        result = isa_repl._compile()
        print("Compilation result:", result)

        result = isa_repl._extract_thm_deps_with_thy_names('get_object_inv')

        result_lst = result.split("<\\SEP>")
        print(result_lst[1:])

        print("length of thms: ", len(result_lst))

        isa_repl._exit()


logic = 'AInvs'
theory_name = 'KHeap_AI'
get_thms(logic, theory_name)
