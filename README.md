**Isa-Repl** wraps a Python REPL for Isabelle based on the [py4j](https://www.py4j.org/) and [scala-isabelle](https://github.com/dominique-unruh/scala-isabelle)

## Prerequisites
- [Isabelle2024](https://isabelle.in.tum.de/website-Isabelle2024/index.html) installed. Either set `ISABELLE_HOME` to the installation path or ensure the `isabelle` executable is on your `PATH`.
- Java 17 or higher.

## Installation
Pre-compiled `IsaREPL.jar` is available on the [releases page](../../releases).

To build from source, see [Compile from source](#compile-from-source) below.

## Usage
Suppose env `ISA_REPL_PATH` is set to the path of the compiled JAR:
```python
import os
import subprocess
import time
from py4j.java_gateway import JavaGateway, GatewayParameters

ISAREPL_PORT = 25556

# Start the JVM server
process = subprocess.Popen(["java", "-jar", os.getenv("ISA_REPL_PATH"), str(ISAREPL_PORT)])
time.sleep(2)

# Connect to the JVM
gateway = JavaGateway(
    gateway_parameters=GatewayParameters(port=ISAREPL_PORT, auto_convert=True)
)
isa_repl = gateway.entry_point

# Initialize the REPL with a theory file
theory_file = os.path.abspath("python-test/Test.thy")
isa_repl._initializeRepl(theory_file)

# Compile the theory environment
isa_repl._compile()

# Step through a lemma and its proof
isa_repl._step('lemma fixes x :: int shows "x ^ 3 = x * x * x" \n proof- \n')
isa_repl._step("show ?thesis by (simp add: numeral_eq_Suc) qed")

# Call sledgehammer on the current goal
isa_repl._step('lemma fixes x :: int shows "x ^ 2 = x * x" \n proof- \n')
isa_repl._prove_by_hammer()

# Apply the SMT translation
isa_repl._translate_to_smt()

# Clean up
isa_repl._exit()
process.terminate()
process.wait()
```


## Compile from source

Make sure [sbt](https://www.scala-sbt.org/1.x/docs/Installing-sbt-on-Linux.html) and scala 2.13.14 is installed. Then just run:
```shell
sbt assembly
```
It will produce `target/IsaREPL.jar`

## Run the integration tests

Tests use [pytest](https://docs.pytest.org/) and share a single JVM via a session-scoped fixture. Install the dependencies:

```shell
pip install pytest py4j
```

Build the JAR, then run tests from the repo root:

```shell
sbt assembly
pytest --capture=no python-test/

# a single file
pytest --capture=no python-test/test_repl.py

# a single test function
pytest --capture=no python-test/test_repl.py::test_repl
```

### Tests that require seL4 l4v

The following tests exercise Isabelle sessions from a local [seL4/l4v](https://github.com/seL4/l4v) checkout. Set `L4V_PATH` before running them:

```shell
export L4V_PATH=/path/to/l4v
```

Tests needing `L4V_PATH` (marked with `@needs_l4v`):

- `test_dependent_thms.py`
- `test_dependent_thm_with_thy.py`
- `test_extract_parent_thms.py`
- `test_hammer_facts_with_thy.py`
- `test_parse_l4v_theory.py`

If `L4V_PATH` is unset, these tests are skipped automatically.
