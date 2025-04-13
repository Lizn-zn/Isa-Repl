package RunIsar
  
import org.scalatest.funsuite.AnyFunSuite
import java.nio.file.Paths
import RunIsar.IsaREPL

class TryCloseTests extends AnyFunSuite {
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

  // Ensure cleanup after tests
  override def withFixture(test: NoArgTest) = {
    try {
      test()
    } finally {
      try {
        isa_repl.exit_isabelle()
      } catch {
        case e: Exception => 
          println(s"Error during cleanup: ${e.getMessage}")
      }
    }
  }

  // 1. compile the theory env
  val result0: String = isa_repl.compile(""" theory test imports Complex_Main  begin""")

  // test 1
  test("try 0 No.1 from Isabelle proof") {
    // create the theorem to be proved
    isa_repl.step("""
                  lemma Node_7 : 
                  fixes e :: "complex" 
                  and r :: "complex" 
                  assumes h0 : "- r + - e = - r - e" 
                  and h1 : "(- r - e)\<^sup>2 = (- r - e) * (- r - e)" 
                  and h2 : "(- r + - e)\<^sup>2 = (- r - e)\<^sup>2" 
                  and h3 : "(- r - e) * (- r - e) = - r * - r + - r * - e + - e * - r + - e * - e" 
                  and h4 : "r\<^sup>2 + r * e + e * r + e\<^sup>2 = r\<^sup>2 + 2 * (e * r) + e\<^sup>2" 
                  and h5 : "- r * - r + - r * - e + - e * - r + - e * - e = r\<^sup>2 + r * e + e * r + e\<^sup>2" 
                  shows "2 * (e * r) + (e\<^sup>2 + r\<^sup>2) = (- r + - e)\<^sup>2"
                """)
    val (ok, result) = isa_repl.try_close()
    assert(ok == true)
    val proof_string: String = result.replace("Try this:", "").replaceAll("\\(\\d+ ms\\)", "")
    println(proof_string)
    val res: String = isa_repl.step(proof_string)
    assert(res == "")
  }
}