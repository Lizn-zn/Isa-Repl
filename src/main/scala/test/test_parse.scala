package RunIsar

import RunIsar.IsaREPL
import RunIsar.Pretty
import de.unruh.isabelle.control.IsabelleMLException
import de.unruh.isabelle.mlvalue.{AdHocConverter, MLFunction}
import de.unruh.isabelle.pure.{Context, ToplevelState}
import de.unruh.isabelle.mlvalue.MLValue.compileFunction
import de.unruh.isabelle.mlvalue.Implicits._
import de.unruh.isabelle.pure.Implicits._
import java.nio.file.{Path, Paths}


object Test_Parse {
  val isabelleHome_str: String = sys.env.getOrElse("ISABELLE_HOME", throw new Exception("ISABELLE_HOME not set"))
  val path_to_isa_bin: String = isabelleHome_str

  val path_to_file : String = Paths.get("python-test/Test.thy").toAbsolutePath.toString
  val working_directory : String = Paths.get(isabelleHome_str, "./src/HOL").toAbsolutePath.toString
  def main(args: Array[String]): Unit = {
    val isa_repl = new IsaREPL(
      path_to_isa_bin = path_to_isa_bin,
      path_to_file = path_to_file,
      working_directory = working_directory,
      debug = true
    )
    
    // 1. compile the file, i.e., Test.thy
    val result0: String = isa_repl.compile()
    println(result0)

    // 2. create the theorem to be proved
    val theorem_string = """
        lemma 
        fixes x :: int 
        shows "x ^ 3 = x * x * x" 
        proof- 
          have eq1: "x ^ 2 = x * x" by auto
          have eq2: "x ^ 3 = x ^ 2 * x" by auto
          show ?thesis by (simp add: eq1 eq2)
        """
    val result1: String = isa_repl.parse_to_steps(theorem_string)
    val steps: List[String] = result1.split("\u001F").toList
    println("length of parsed result: " + steps.length)
    println("parsed result: " + steps)

    println("success")

  }
}