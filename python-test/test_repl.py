import os
import subprocess
import time
import socket
from py4j.java_gateway import JavaGateway, GatewayParameters
import pytest

def wait_for_port(port, host='127.0.0.1', timeout=30.0):
    """Wait until a port starts accepting TCP connections.
    Args:
        port: Port number.
        host: Host address on which the port should exist.
        timeout: In seconds. How long to wait before raising errors.
    Raises:
        TimeoutError: The port isn't accepting connection after time specified in `timeout`.
    """
    start_time = time.perf_counter()
    while True:
        try:
            with socket.create_connection((host, port), timeout=timeout):
                return
        except OSError as ex:
            time.sleep(0.01)
            if time.perf_counter() - start_time >= timeout:
                raise TimeoutError(f'Waited too long for the port {port} on host {host} to start accepting connections.') from ex

def test_repl():
    jar_path = os.getenv("ISA_REPL_PATH")
    if not jar_path:
        raise ValueError("ISA_REPL_PATH environment variable not set")
    
    # Start the Java process
    process = subprocess.Popen([
        "java", "-jar", jar_path, str(25555)
    ])
    
    try:
        # Wait for the server to start
        wait_for_port(25555)
        
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
        
    finally:
        # Clean up
        if 'gateway' in locals():
            gateway.shutdown()
        if process.poll() is None:  # If process is still running
            process.terminate()
            try:
                process.wait(timeout=5)
            except subprocess.TimeoutExpired:
                process.kill()

