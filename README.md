## Main process
IsaPy wraps a Python REPL for Isabelle based on the [py4j] and [scala-isabelle]

## Installation
#### 1. Clone the repository
```shell
git clone https://github.com/Lizn-zn/scala-isabelle
cd scala-isabelle
sbt publishLocal
```

#### 2. Isabelle [Isabelle], and set the environment variable `ISABELLE_HOME` to indicate Isabelle installation.
```shell
export ISABELLE_HOME=/path/to/Isabelle2024/
```

#### 3. Install [Scala](https://www.scala-sbt.org/1.x/docs/zh-cn/Installing-sbt-on-Linux.html) and 

#### 4. 


<!-- 2. Install PySpark:  
```shell
pip install pyspark
```
Run `pyspark --version` to check the installation. Please be careful about the pyspark version.

3. Compile and create a JAR file with all the dependencies included:  
```shell
sbt assembly
```
This command will create a JAR file at `/target/scala-2.12/scala-isabelle-assembly-1.0.jar`.

4. Run `python-test/test.py` to check whether the JAR file has been successfully created.
```shell
Transition: ""
Transition: "theory Test imports Main HOL.Real begin"
Transition: ""
Transition: "lemma fixes a :: real shows "a^2+2*a+1 >= 0""
Transition: ""
(true,(some,List(Try this: by (metis ab_semigroup_mult_class.mult_ac(1) add.commute add.left_commute mult.commute mult.right_neutral power2_eq_square power2_sum ring_class.ring_distribs(1) ring_class.ring_distribs(2) zero_le_square))))
Text( theory Test imports Main HOL.Real begin lemma fixes a :: real shows "a^2+2*a+1 >= 0" ,./Test.thy,position (computing))
```

5. To run the isabelle checker in Java, you should copy the jar file to the Java lib.
```shell
cp target/scala-2.12/scala-isabelle-assembly-1.0.jar ../JaChecker/demo/lib/
```
   
## More tips

You can modify ```theorySource``` in [RepHammer.scala](scala-isa-project/src/main/scala/test/RepHammer.scala) and then run sbt run directly for quick debugging. When you want to update the jar package, you need to re-run ```sbt assembly```