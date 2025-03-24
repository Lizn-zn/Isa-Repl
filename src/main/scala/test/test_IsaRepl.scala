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


object Test {
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
    
    // compile the file, i.e., Test.thy
    val result0: String = isa_repl.step_to_transition_text("")
    println(result0)

    val theorem_string = "lemma fixes x :: int shows \"x ^ 3 = x * x * x\" \n proof- \n"
    val result1: String = isa_repl.step(theorem_string)
    println(result1)

    val proof_string1 = "have eq4: \"tan_deg (-48) = tan_deg 312\""
    val result2: String = isa_repl.step(proof_string1)
    println(result2)

    implicit val isabelle = isa_repl.isabelle
    implicit val ec = isa_repl.ec
    val facts_of : MLFunction[ToplevelState, List[String]] = compileFunction[ToplevelState, List[String]](
    """fn tls => map Pretty.unformatted_string_of (let
      |    val ctxt = (Toplevel.context_of tls);
      |    val facts = Proof_Context.facts_of ctxt;
      |    val props = map #1 (Facts.props facts);
      |    val true_global_facts =
      |      (if null props then [] else [("<unnamed>", props)]) @
      |      Facts.dest_static false [Global_Theory.facts_of (Proof_Context.theory_of ctxt)] facts;
      |  in
      |    if null true_global_facts then []
      |    else
      |      [Pretty.big_list "true_global facts:"
      |        (map #1 (sort_by (#1 o #2) (map (`(Proof_Context.pretty_fact ctxt)) true_global_facts)))]
      |  end)""".stripMargin
    )

    for (fact <- facts_of(isa_repl.toplevel).force.retrieveNow) {
        println(fact)
    }

    val proof_string = "by (simp add: numeral_eq_Suc)"
    val parsed : String = isa_repl.step_to_transition_text(proof_string)
    println(parsed)



    // val proof_string2 = "by auto"
    // try{
    //     val result2: String = isa_repl.step(proof_string2)
    //     println(result2)
    // } catch {
    //     case _: IsabelleMLException => println("failed")
    // }


    // val proof_string3 = "by (smt (verit) eq2)"
    // val result3: String = isa_repl.step(proof_string3)
    // println(result3)


    // val local_theorems : MLFunction[ToplevelState, List[String]] = compileFunction[ToplevelState, List[String]](
    //     """fn tls => map Pretty.unformatted_string_of (Proof_Context.pretty_local_facts false (Toplevel.context_of tls))"""
    // )
    // val local_ts = local_theorems(isa_repl.toplevel).force.retrieveNow
    // println(local_ts.length)
  }
}