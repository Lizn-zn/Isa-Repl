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


object Test_Hammer {

  def main(args: Array[String]): Unit = {
    // get the value of isabelleHome_str from env variable ISABELLE_HOME. If not set, raise an error
    val isabelleHome_str: String = sys.env.getOrElse("ISABELLE_HOME", throw new Exception("ISABELLE_HOME not set"))
  
    val isabelleHome: Path = Paths.get(isabelleHome_str)
    val setup: Setup = Setup(isabelleHome = isabelleHome)

    val theoryManager: TheoryManager = new TheoryManager(
      path_to_isa_bin=isabelleHome_str,
      wd=Paths.get(isabelleHome_str, "./src/HOL").toAbsolutePath.toString
    )
    implicit val isabelle: Isabelle = new Isabelle(setup)

    val theorySource = TheoryManager.Text(
      """ theory Test imports Main HOL.HOL HOL.Real Complex_Main begin lemma fixes a :: real shows "a ^ 2 + 2 * a + 1 >= 0" """,
      Paths.get("Test.thy").toAbsolutePath)
    println(theorySource)

    val thy0 = theoryManager.beginTheory(theorySource)
    val init_toplevel: MLFunction0[ToplevelState] = 
      if (Version.from2023)
        compileFunction0[ToplevelState]("fn _ => Toplevel.make_state NONE") 
      else
        compileFunction0[ToplevelState]("Toplevel.init_toplevel")
    var toplevel = init_toplevel().force.retrieveNow

    val parse_text = compileFunction[Theory, String, List[(Transition.T, String)]](
      """fn (thy, text) => let
        |  val transitions = Outer_Syntax.parse_text thy (K thy) Position.start text
        |  fun addtext symbols [tr] =
        |        [(tr, implode symbols)]
        |    | addtext _ [] = []
        |    | addtext symbols (tr::nextTr::trs) = let
        |        val (this,rest) = Library.chop (Position.distance_of (Toplevel.pos_of tr, Toplevel.pos_of nextTr) |> Option.valOf) symbols
        |        in (tr, implode this) :: addtext rest (nextTr::trs) end
        |  in addtext (Symbol.explode text) transitions end""".stripMargin)

    val command_exception = compileFunction[Boolean, Transition.T, ToplevelState, ToplevelState](
      "fn (int, tr, st) => Toplevel.command_exception int tr st")

    val theory_of_state: MLFunction[_, _] =   
    if (Version.from2023)  
      compileFunction[Theory, ToplevelState]("Toplevel.make_state o SOME")  
    else  
      compileFunction[ToplevelState, Theory]("Toplevel.theory_of")  
    val context_of_state: MLFunction[ToplevelState, Context] =
      compileFunction[ToplevelState, Context]("Toplevel.context_of")
    val name_of_transition: MLFunction[Transition.T, String] =
      compileFunction[Transition.T, String]("Toplevel.name_of")
      
    for ((transition, text) <- parse_text(thy0, theorySource.text).force.retrieveNow) {
      println(context_of_state(toplevel).retrieveNow)
      println(s"""Transition: "${text.strip}"""")
      toplevel = command_exception(true, transition, toplevel).retrieveNow.force
    }

    //    val finalThy = toplevel_end_theory(toplevel).retrieveNow.force

    val thy_for_sledgehammer = thy0
    val Sledgehammer: String = thy_for_sledgehammer.importMLStructureNow("Sledgehammer")
    val Sledgehammer_Commands: String = thy_for_sledgehammer.importMLStructureNow("Sledgehammer_Commands")
    val Sledgehammer_Prover: String = thy_for_sledgehammer.importMLStructureNow("Sledgehammer_Prover")

    val normal_with_Sledgehammer: MLFunction4[ToplevelState, Theory, List[String], List[String], (Boolean, (String, List[String]))] =
      compileFunction[ToplevelState, Theory, List[String], List[String], (Boolean, (String, List[String]))](
        s""" fn (state, thy, adds, dels) =>
           |    let
           |       val override = {add=[],del=[],only=false};
           |       fun go_run (state, thy) =
           |          let
           |             val p_state = Toplevel.proof_of state;
           |             val ctxt = Proof.context_of p_state;
           |             val params = ${Sledgehammer_Commands}.default_params thy
           |                [("provers", "cvc4 vampire verit e spass z3 zipperposition"),("timeout","60"),("verbose","true")];
           |             val results = ${Sledgehammer}.run_sledgehammer params ${Sledgehammer_Prover}.Normal NONE 1 override p_state;
           |             val (result, (outcome, step)) = results;
           |           in
           |             (result, (${Sledgehammer}.short_string_of_sledgehammer_outcome outcome, [YXML.content_of step]))
           |           end;
           |    in
           |      Timeout.apply (Time.fromSeconds 180) go_run (state, thy) end
           |""".stripMargin
      )

    // Apply transitions to toplevel such that it is at a "hammerable" place
    // Then use sledgehammer to prove the theorem
    println("hammering...")
    val result = normal_with_Sledgehammer(toplevel, thy0, List[String](), List[String]()).force.retrieveNow
    // (true,(some,List(Try this: by (smt (verit) mult_cancel_right2 one_power2 power2_sum power_0 power_commutes zero_le_power2) (222 ms))))
    println(result)

    println("success")

  }

}