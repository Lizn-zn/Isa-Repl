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


object Test_IsaRepl_Case1 {
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

    //   lemma 
    //   fixes x :: int 
    //   shows "x ^ 3 = x * x * x" 
    //   proof-
    //     have "x = x" by simp
    //     have "x ^ 2 = x * x" by simp
    //     have "x ^ 4 = x ^ 2 * x ^ 2" by simp
    //     show ?thesis by (simp add: numeral_eq_Suc)

    // 2. create the theorem to be proved
    val theorem_string = "lemma fixes x :: int shows \"x ^ 3 = x * x * x\" \n proof- \n"
    val result1: String = isa_repl.step(theorem_string)
    println(result1)

    // 3. declare a new lemma
    val proof_string1 = "have eq1: \"x ^ 4 = x * x * x * x\""
    val result2: String = isa_repl.step(proof_string1)
    println(result2)

    // 4. try "by simp" to prove the eq4
    val proof_string2 = "by simp"
    try{
        val result3: String = isa_repl.step(proof_string2)
        println(result3)
    } catch {
      case _: IsabelleMLException => 
        println("failed for prove eq1 using `by simp`")
    }

    // 5. use "sorry" to skip the proof
    val proof_string3 = "sorry"
    val result4: String = isa_repl.step(proof_string3)
    println(result4)

    // 6. declare the lemma again
    var proof_string4 = "have eq2: \"x ^ 2 = x * x\"" 
    val result5: String = isa_repl.step(proof_string4)
    println(result5)

    // 7. prove the lemma
    var proof_string5 = "by (simp add: power2_eq_square)"
    val result6: String = isa_repl.step(proof_string5)
    println(result6)

    val goal : String = isa_repl.extract_goal()
    println("goal: " + goal)
    
    // 8. close the proof
    val proof_string6 = "show ?thesis by (simp add: numeral_eq_Suc)"
    val result7: String = isa_repl.step(proof_string6)
    println(result7)

    println("success")

  }
}