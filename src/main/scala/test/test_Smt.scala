package RunIsar

import de.unruh.isabelle.control.Isabelle
import de.unruh.isabelle.control.Isabelle.Setup
import de.unruh.isabelle.mlvalue.{MLValue, MLFunction0, MLFunction, MLFunction4, Version}
import de.unruh.isabelle.mlvalue.MLValue.{compileFunction, compileFunction0}
import de.unruh.isabelle.mlvalue.AdHocConverter
import de.unruh.isabelle.pure.{Context, Theory, TheoryHeader, ToplevelState}
import de.unruh.isabelle.control.{Isabelle, OperationCollection}
import de.unruh.isabelle.pure.{Position, Theory, TheoryHeader}

import java.nio.file.{Path, Paths}

import de.unruh.isabelle.mlvalue.Implicits._
import de.unruh.isabelle.pure.Implicits._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Future, Await}
import scala.concurrent.duration.Duration


object Test_Smt {

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

    val Skip_Proof : String = thy0.importMLStructureNow("Skip_Proof")
    val SMT_Config : String = thy0.importMLStructureNow("SMT_Config")
    val SMT_Normalize : String = thy0.importMLStructureNow("SMT_Normalize")
    val SMT_Util : String = thy0.importMLStructureNow("SMT_Util")
    val SMT_Translate : String = thy0.importMLStructureNow("SMT_Translate")
    val translate_to_smt: MLFunction[ToplevelState, String] =  
        compileFunction[ToplevelState, String](  
        s""" fn (state) =>  
            |    let  
            |       val p_state = Toplevel.proof_of state;  
            |       val thy = Toplevel.theory_of state;
            |       val ctxt = Proof.context_of p_state;  
            |
            |       (* Load some lemmas previously. *)  
            |       val TrueI = Proof_Context.get_thm ctxt "TrueI";
            |       val ccontr = Proof_Context.get_thm ctxt "ccontr";
            |  
            |       (* Extract the assumptions and the conclusion of the theorem. *)  
            |       val {context = _, facts, goal} = Proof.goal p_state;  
            |       val assumptions = Assumption.all_prems_of ctxt;  
            |       val goals = map (Skip_Proof.make_thm thy) (Thm.prems_of goal); 
            |  
            |  
            |       (* Put the assumptions in facts and the conclusion in goal. *)  
            |       val options = ${SMT_Config}.solver_options_of ctxt;  
            |       val comments = [space_implode " " options];  
            |       val has_topsort = Term.exists_type (Term.exists_subtype (fn  
            |          TFree (_, []) => true  
            |         | TVar (_, []) => true  
            |         | _ => false));  
            |       fun check_topsort ctxt thm =  
            |         if has_topsort (Thm.prop_of thm) then (${SMT_Normalize}.drop_fact_warning ctxt thm; TrueI) else thm  
            |
            |       val thms0 = facts @ assumptions;  
            |       val thms = map (pair ${SMT_Util}.Axiom o check_topsort ctxt) thms0; 
            |       val assms_thms = (${SMT_Normalize}.normalize ctxt thms);
            |       
            |       val thms0 = goals;  
            |       val thms = map (pair ${SMT_Util}.Conjecture o check_topsort ctxt) thms0; 
            |       val conc_thms = (${SMT_Normalize}.normalize ctxt thms);
            |
            |       val ithms = assms_thms @ conc_thms;
            |  
            |       fun go_run () = 
            |         let 
            |           val (str, _) = ${SMT_Translate}.translate ctxt "z3" [] comments ithms
            |         in 
            |           str  end  
            |    in  
            |       Timeout.apply (Time.fromSeconds 180) go_run () end 
            |""".stripMargin  
        )  
    // Apply transitions to toplevel such that it is at a "hammerable" place
    // Then use sledgehammer to prove the theorem
    println("translating to smt...")
    val result = translate_to_smt(toplevel).force.retrieveNow
    println(result)

    println("success")

  }
}