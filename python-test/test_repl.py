import os
import subprocess
import time
import socket
import logging
from py4j.java_gateway import JavaGateway, GatewayParameters
import pytest

# 设置日志
logging.basicConfig(level=logging.DEBUG)
logger = logging.getLogger(__name__)

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
    
    logger.info(f"Using JAR path: {jar_path}")
    
    # Start the Java process
    process = subprocess.Popen([
        "java", "-jar", jar_path, str(25555)
    ], stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    
    try:
        # Wait for the server to start
        logger.info("Waiting for server to start...")
        wait_for_port(25555)
        logger.info("Server started successfully")
        
        # Connect to the JVM
        logger.info("Connecting to JVM...")
        gateway = JavaGateway(gateway_parameters=GatewayParameters(port=25555))
        logger.info("Connected to JVM")
        
        # Get the IsaREPL application
        logger.info("Getting IsaREPL application...")
        isa_repl = gateway.entry_point
        logger.info("Got IsaREPL application")
        
        # Initialize REPL with a theory file
        theory_file = os.path.abspath("python-test/Test.thy")
        logger.info(f"Initializing REPL with theory file: {theory_file}")
        
        try:
            isa_repl._initializeRepl(theory_file)
            logger.info("REPL initialized successfully")
        except Exception as e:
            logger.error(f"Error initializing REPL: {str(e)}")
            if hasattr(e, 'java_exception'):
                logger.error(f"Java exception: {e.java_exception}")
            raise
        
        # Compile the theory file
        logger.info("Compiling theory file...")
        result = isa_repl._compile()
        assert result == "True<\\SEP>"
        logger.info("Theory file compiled successfully")
        
        # Create and prove a theorem
        theorem = "lemma fixes x :: int shows \"x ^ 3 = x * x * x\" \n proof- \n"
        logger.info("Creating theorem...")
        result = isa_repl._step(theorem)
        assert "1. x ^ 3 = x * x * x" in result
        logger.info("Theorem created successfully")
        
        # Add a proof step
        proof_step = "show ?thesis by (simp add: numeral_eq_Suc)"
        logger.info("Adding proof step...")
        result = isa_repl._step(proof_step)
        assert "No subgoals!" in result
        logger.info("Proof step added successfully")
        
        # Close the REPL
        logger.info("Closing REPL...")
        isa_repl._exit()
        logger.info("REPL closed successfully")
        
    except Exception as e:
        logger.error(f"Test failed with error: {str(e)}")
        # 打印 Java 进程的输出
        stdout, stderr = process.communicate()
        if stdout:
            logger.error(f"Java stdout: {stdout.decode()}")
        if stderr:
            logger.error(f"Java stderr: {stderr.decode()}")
        raise
        
    finally:
        # Clean up
        logger.info("Cleaning up...")
        if 'gateway' in locals():
            gateway.shutdown()
        if process.poll() is None:  # If process is still running
            process.terminate()
            try:
                process.wait(timeout=5)
            except subprocess.TimeoutExpired:
                process.kill()
        logger.info("Cleanup complete")

