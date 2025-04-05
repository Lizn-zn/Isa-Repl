## Main process
Isa-Repl wraps a Python REPL for Isabelle based on the [py4j] and [scala-isabelle]

## Installation
#### 1. Clone the repository
```
git clone https://github.com/Lizn-zn/Isa-Repl
```

#### 2. Path configuration
```
export ISABELLE_HOME=/path/to/Isabelle2024/
export ISA_REPL_PATH=/path/to/Isa-Repl/target/IsaREPL.jar
```

## Usage

```python
# Start the JVM server
process = subprocess.Popen(["java", "-jar", os.getenv("ISA_REPL_PATH"), 25333])

# Initialize the REPL
theory_file = os.path.abspath("python-test/Test.thy")
isa_repl.initializeRepl(theory_file)

# Compile the theorem environment
isa_repl.compile("theory Test imports Main HOL.HOL HOL.Real Complex_Main")

# Step the theorem or proof
isa_repl.step("lemma fixes x :: int shows \"x ^ 3 = x * x * x\" \n proof- \n")
isa_repl.step("show ?thesis by (simp add: numeral_eq_Suc) qed")

# Call the sledgehammer
isa_repl.step("lemma fixes x :: int shows \"x ^ 2 = x * x\" \n proof- \n")
isa_repl.prove_by_hammer()

# Apply the SMT translation
isa_repl.step("lemma fixes x :: int shows \"x ^ 2 = x * x\" \n proof- \n")
isa_repl.translate_to_smt()
```


## JAR Compilation
#### 1. Clone the repository
```shell
git clone https://github.com/Lizn-zn/scala-isabelle
cd scala-isabelle
sbt publishLocal
```

#### 2. Install [Isabelle], and set the environment variable `ISABELLE_HOME` to indicate Isabelle installation.
```shell
export ISABELLE_HOME=/path/to/Isabelle2024/
```

#### 3. Install [Scala](https://www.scala-sbt.org/1.x/docs/zh-cn/Installing-sbt-on-Linux.html). Run the following command to check whether the installation is successful.
```shell
./src/test/test.sh 
```

#### 4. Compile and create a JAR file at `target/IsaREPL.jar` with all the dependencies included.
```
sbt assembly
```

#### Test Python-JVM connection

#### 1. Start JVM server 
```
java -jar target/IsaREPL.jar 25555
```

#### 2. Test the JVM server 
```
python python-test/test_repl.py
```
