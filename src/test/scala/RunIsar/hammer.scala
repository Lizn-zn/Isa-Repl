package RunIsar
  
import org.scalatest.funsuite.AnyFunSuite
import java.nio.file.Paths
import RunIsar.IsaREPL

class HammerTests extends AnyFunSuite {
  // get the value of isabelleHome_str from env variable ISABELLE_HOME. If not set, raise an error
  val isabelleHome_str: String = sys.env.getOrElse("ISABELLE_HOME", throw new Exception("ISABELLE_HOME not set"))
  val path_to_isa_bin: String = isabelleHome_str

  val path_to_file : String = Paths.get("python-test/Test.thy").toAbsolutePath.toString
  val working_directory : String = Paths.get(isabelleHome_str, "./src/HOL").toAbsolutePath.toString
  val isa_repl = new IsaREPL(
    path_to_isa_bin = path_to_isa_bin,
    path_to_file = path_to_file,
    working_directory = working_directory,
    debug = false
  )

  // 1. compile the theory env
  val result0: String = isa_repl.compile(""" theory Test imports Main HOL.HOL HOL.Real begin""")

  // test 1
  test("Hammer No.1 from Isabelle proof") {
    // create the theorem to be proved
  isa_repl.step("""
                lemma test: fixes a b :: real
                assumes ha : "a > 0" and hb : "b > 0"
                shows   "a + b > 0"
                proof-
                  show ?thesis
                """)
    val (ok, result) = isa_repl.prove_by_hammer()
    assert(ok == true)
    val proof_string: String = result.replace("Try this:", "").replaceAll("\\(\\d+ ms\\)", "")
    val res: String = isa_repl.step(proof_string)
    assert(res.contains("No subgoals!"))
    isa_repl.step("qed")
  }
}