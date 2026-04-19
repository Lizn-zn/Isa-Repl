import os
import subprocess
import time
from py4j.java_gateway import JavaGateway, GatewayParameters


def run_jar_file(jar_path, port):
    env = os.environ.copy()
    env["ISABELLE_HOME"] = os.path.expanduser("~/verification/isabelle/")
    process = subprocess.Popen(["java", "-jar", jar_path, str(port)], env=env)
    time.sleep(2)
    return process


def isapy_repl(port):
    gateway = JavaGateway(
        gateway_parameters=GatewayParameters(port=port, auto_convert=True)
    )
    return gateway.entry_point


def split_result(result, maxsplit=1):
    parts = result.split("<\\SEP>", maxsplit)
    if len(parts) != maxsplit + 1:
        raise AssertionError(f"Malformed result: {result}")
    return parts


def test_explicitize_simp(isa_repl):
    theory_file = os.path.abspath("python-test/Test.thy")

    ok, msg = split_result(isa_repl._initializeRepl(theory_file))
    assert ok == "True", msg

    ok, msg = split_result(isa_repl._compile())
    assert ok == "True", msg

    ok, state = split_result(isa_repl._step('lemma "(3::nat) <= (4::nat)"'))
    assert ok == "True", state

    parts = split_result(isa_repl._explicitize_proof("simp"), maxsplit=3)
    api_ok, solved, command, trace_log = parts
    assert api_ok == "True", trace_log

    print("Solved flag:", solved)
    print("Explicit command:", command)
    print("Trace preview:\n", "\n".join(trace_log.splitlines()[:20]))

    assert solved.lower() == "true", trace_log
    assert command.strip() != "", "Empty explicit command"

    ok, after_apply = split_result(isa_repl._step(command))
    assert ok == "True", after_apply

    # _proof_finished in this gateway is not reliable once we leave proof mode.
    # Successful execution of the explicit command is the validation signal here.


if __name__ == "__main__":
    jar_path = "target/IsaREPL.jar"
    port = 25557
    jvm_process = run_jar_file(jar_path, port)

    try:
        isa_repl = isapy_repl(port)
        test_explicitize_simp(isa_repl)
        print("test_explicitize_simp passed")
    finally:
        jvm_process.terminate()
        jvm_process.wait()
