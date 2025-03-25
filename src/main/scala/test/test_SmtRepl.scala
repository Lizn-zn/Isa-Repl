package RunIsar

import de.unruh.isabelle.control.Isabelle
import de.unruh.isabelle.control.Isabelle.Setup
import de.unruh.isabelle.mlvalue.{MLValue, MLFunction0, MLFunction, MLFunction4, Version}
import de.unruh.isabelle.mlvalue.MLValue.{compileFunction, compileFunction0}
import de.unruh.isabelle.mlvalue.AdHocConverter
import de.unruh.isabelle.pure.{Context, Theory, TheoryHeader, ToplevelState}
import de.unruh.isabelle.control.{Isabelle, OperationCollection}
import de.unruh.isabelle.mlvalue.MLValue.compileFunction
import de.unruh.isabelle.pure.{Position, Theory, TheoryHeader}

import java.nio.file.{Path, Paths}

import de.unruh.isabelle.mlvalue.Implicits._
import de.unruh.isabelle.pure.Implicits._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Future, Await}
import scala.concurrent.duration.Duration


object Test_SmtRepl {

  // get the value of isabelleHome_str from env variable ISABELLE_HOME. If not set, raise an error
  val isabelleHome_str: String = sys.env.getOrElse("ISABELLE_HOME", throw new Exception("ISABELLE_HOME not set"))
  val path_to_isa_bin: String = isabelleHome_str

  val path_to_file : String = Paths.get("python-test/Test.thy").toAbsolutePath.toString
  val working_directory : String = Paths.get(isabelleHome_str, "./src/HOL").toAbsolutePath.toString
  def main(args: Array[String]): Unit = {
    val isa_repl = new IsaREPL(
      path_to_isa_bin = path_to_isa_bin,
      path_to_file = path_to_file,
      working_directory = working_directory
    )

    // 1. compile the theory env
    val result0: String = isa_repl.compile(""" theory Test imports Main HOL.HOL HOL.Real """)
    println(result0)

    // 2. create the theorem to be proved
    val result1: String = isa_repl.step("""
                                          lemma test: fixes a :: real
                                          assumes ha : "a > 0"
                                          shows   "a ^ 2 + 2 * a + 1 > 0"
                                          proof-
                                            show ?thesis
                                        """)
    println(result1)

    // 3. translate the theorem to SMT
    val result2: String = isa_repl.translate_to_smt()
    println(result2)
    
    // 4. close the proof
    val proof_string: String = "sorry"
    val result3: String = isa_repl.step_without_timeout(proof_string)
    println(result3)
    
    println("success")

  }

}