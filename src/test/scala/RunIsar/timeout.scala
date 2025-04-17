package RunIsar
  
import org.scalatest.funsuite.AnyFunSuite
import java.nio.file.Paths
import RunIsar.IsaREPL
import RunIsar.RunIsarMLException

class TimeoutTests extends AnyFunSuite {
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
  val result0: String = isa_repl.compile(""" theory test imports Complex_Main "HOL-Computational_Algebra.Computational_Algebra" "HOL-Number_Theory.Number_Theory" begin """)

  // test 1
  test("try 0 No.1 from Isabelle proof") {
    // create the theorem to be proved
    val ok = isa_repl.test_timeout()
    assert(ok == false)
  }
}