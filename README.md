## Main process
IsaPy wraps a Python REPL for Isabelle based on the [py4j] and [scala-isabelle]

## Installation

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