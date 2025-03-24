package RunIsar

import java.nio.file.{Path, Paths}
import scala.collection.JavaConverters._

import util.control.Breaks
import scala.collection.mutable.ListBuffer
import scala.concurrent.{
  Await,
  ExecutionContext,
  Future,
  TimeoutException,
  blocking
}
import scala.concurrent.duration.Duration
import scala.util.{Success, Failure}
import sys.process._
import _root_.java.nio.file.{Files, Path}
import _root_.java.io.File

import de.unruh.isabelle.control.Isabelle
import de.unruh.isabelle.mlvalue.{
  AdHocConverter,
  MLFunction,
  MLFunction0,
  MLFunction2,
  MLFunction3,
  MLFunction4,
  MLValue,
  MLValueWrapper,
  Version
}
import de.unruh.isabelle.mlvalue.MLValue.{
  compileFunction,
  compileFunction0,
  compileValue
}
import de.unruh.isabelle.pure.{
  Context,
  Position,
  Theory,
  TheoryHeader,
  ToplevelState
}

// import RunIsar.TheoryManager
import RunIsar.TheoryManager.{Ops, Source, Text}
// Implicits
import de.unruh.isabelle.mlvalue.Implicits._
import de.unruh.isabelle.pure.Implicits._
import de.unruh.isabelle.control.IsabelleMLException

object Transition extends AdHocConverter("Toplevel.transition")
object ProofState extends AdHocConverter("Proof.state")
object RuntimeError extends AdHocConverter("Runtime.error")
object Pretty extends AdHocConverter("Pretty.T")
object ProofContext extends AdHocConverter("Proof_Context.T")

class IsaREPL(
    var path_to_isa_bin: String,
    var path_to_file: String,
    var working_directory: String,
    var debug: Boolean = true
) {
  if (debug) println("Checkpoint 1: Isabelle setup")
  // Prepare setup config and the implicit Isabelle context
  var currentTheoryName: String =
  path_to_file.split("/").last.replace(".thy", "")
  val isabelleHome: Path = Paths.get(path_to_isa_bin)
  val setup: Isabelle.Setup = Isabelle.Setup(isabelleHome = isabelleHome, workingDirectory = Path.of(working_directory))
  implicit val isabelle: Isabelle = new Isabelle(setup)
  implicit val ec: ExecutionContext = ExecutionContext.global
  if (debug) println("Checkpoint 2: Compile ML functions")
  // Compile useful ML functions
  val script_thy: MLFunction2[String, Theory, Theory] =
    compileFunction[String, Theory, Theory](
      "fn (str,thy) => Thy_Info.script_thy Position.none str thy"
    )
  val init_toplevel: MLFunction0[ToplevelState] = 
    if (Version.from2023)
      compileFunction0[ToplevelState]("fn _ => Toplevel.make_state NONE") 
    else
      compileFunction0[ToplevelState]("Toplevel.init_toplevel")
  val is_proof: MLFunction[ToplevelState, Boolean] =
    compileFunction[ToplevelState, Boolean]("Toplevel.is_proof")
  val is_skipped_proof: MLFunction[ToplevelState, Boolean] =
    compileFunction[ToplevelState, Boolean]("Toplevel.is_skipped_proof")
  val proof_level: MLFunction[ToplevelState, Int] =
    compileFunction[ToplevelState, Int]("Toplevel.level")
  val proof_of: MLFunction[ToplevelState, ProofState.T] =
    compileFunction[ToplevelState, ProofState.T]("Toplevel.proof_of")
  val command_exception
      : MLFunction3[Boolean, Transition.T, ToplevelState, ToplevelState] =
    compileFunction[Boolean, Transition.T, ToplevelState, ToplevelState](
      "fn (int, tr, st) => Toplevel.command_exception int tr st"
    )
  val command_exception_with_10s_timeout
      : MLFunction3[Boolean, Transition.T, ToplevelState, ToplevelState] =
    compileFunction[Boolean, Transition.T, ToplevelState, ToplevelState](
      """fn (int, tr, st) => let
        |  fun go_run (a, b, c) = Toplevel.command_exception a b c
        |  in Timeout.apply (Time.fromSeconds 10) go_run (int, tr, st) end""".stripMargin
    )
  val command_exception_with_30s_timeout
      : MLFunction3[Boolean, Transition.T, ToplevelState, ToplevelState] =
    compileFunction[Boolean, Transition.T, ToplevelState, ToplevelState](
      """fn (int, tr, st) => let
        |  fun go_run (a, b, c) = Toplevel.command_exception a b c
        |  in Timeout.apply (Time.fromSeconds 30) go_run (int, tr, st) end""".stripMargin
    )
  val command_errors: MLFunction3[
    Boolean,
    Transition.T,
    ToplevelState,
    (List[RuntimeError.T], Option[ToplevelState])
  ] = compileFunction[
    Boolean,
    Transition.T,
    ToplevelState,
    (List[RuntimeError.T], Option[ToplevelState])
  ]("fn (int, tr, st) => Toplevel.command_errors int tr st")
  val toplevel_end_theory: MLFunction[ToplevelState, Theory] =
    compileFunction[ToplevelState, Theory]("Toplevel.end_theory Position.none")
  val theory_of_state: MLFunction[_, _] =   
    if (Version.from2023)  
      compileFunction[Theory, ToplevelState]("Toplevel.make_state o SOME")  
    else  
      compileFunction[ToplevelState, Theory]("Toplevel.theory_of")  
  val context_of_state: MLFunction[ToplevelState, Context] =
    compileFunction[ToplevelState, Context]("Toplevel.context_of")
  val name_of_transition: MLFunction[Transition.T, String] =
    compileFunction[Transition.T, String]("Toplevel.name_of")
  val parse_text: MLFunction2[Theory, String, List[(Transition.T, String)]] =
    compileFunction[Theory, String, List[(Transition.T, String)]]("""fn (thy, text) => let
        |  val transitions = Outer_Syntax.parse_text thy (K thy) Position.start text
        |  fun addtext symbols [tr] =
        |        [(tr, implode symbols)]
        |    | addtext _ [] = []
        |    | addtext symbols (tr::nextTr::trs) = let
        |        val (this,rest) = Library.chop (Position.distance_of (Toplevel.pos_of tr, Toplevel.pos_of nextTr) |> Option.valOf) symbols
        |        in (tr, implode this) :: addtext rest (nextTr::trs) end
        |  in addtext (Symbol.explode text) transitions end""".stripMargin)
  val toplevel_string_of_state: MLFunction[ToplevelState, String] =
    compileFunction[ToplevelState, String](
      "fn (s) => YXML.content_of (Toplevel.string_of_state s)"
    )
  val pretty_local_facts: MLFunction2[ToplevelState, Boolean, List[Pretty.T]] =
    compileFunction[ToplevelState, Boolean, List[Pretty.T]](
      "fn (tls, b) => Proof_Context.pretty_local_facts b (Toplevel.context_of tls)"
    )
  val make_pretty_list_string_list: MLFunction[List[Pretty.T], List[String]] =
    compileFunction[List[Pretty.T], List[String]](
      "fn (pretty_list) => map Pretty.unformatted_string_of pretty_list"
    )

  val local_facts_and_defs: MLFunction[ToplevelState, List[(String, String)]] =
    compileFunction[ToplevelState, List[(String, String)]](
      """fn tls =>
        |  let val ctxt = Toplevel.context_of tls;
        |      val facts = Proof_Context.facts_of ctxt;
        |      val props = map #1 (Facts.props facts);
        |      val local_facts =
        |        (if null props then [] else [("unnamed", props)]) @
        |        Facts.dest_static true [Global_Theory.facts_of (Proof_Context.theory_of ctxt)] facts;
        |      val thms = (
        |           if null local_facts then []
        |           else
        |           (map (fn e => #2 (#2 e)) (sort_by (#1 o #2) (map (`(Proof_Context.pretty_fact ctxt)) local_facts))));
        |      val condensed_thms = fold (fn x => fn y => (x @ y)) thms [];
        |  in 
        |      map (fn thm => (
        |            Thm.get_name_hint thm,
        |            Pretty.unformatted_string_of
        |          (Element.pretty_statement ctxt "" thm)
        |         ))
        |         condensed_thms
        |  end""".stripMargin
    )
  val global_facts_and_defs: MLFunction[ToplevelState, List[(String, String)]] =
    compileFunction[ToplevelState, List[(String, String)]](
      """fn tls =>
          | map (fn tup => (#1 tup, Pretty.unformatted_string_of (Element.pretty_statement (Toplevel.context_of tls) "test" (#2 tup))))
          | (Global_Theory.all_thms_of (Proof_Context.theory_of (Toplevel.context_of tls)) false)
          """.stripMargin
    )
  val fact_definition: MLFunction2[ToplevelState, String, String] =
    compileFunction[ToplevelState, String, String](
      """fn (tls, name) =>
        | let val ctxt = Toplevel.context_of tls;
        |     val thm = Global_Theory.get_thms (Proof_Context.theory_of ctxt) name;
        | in
        |     YXML.content_of (Pretty.unformatted_string_of (Element.pretty_statement ctxt "" (hd thm)))
        | end""".stripMargin
    )
  def fact_definition(tls_name: String, theorem_name: String): String = {
    val toplevel_state = retrieve_tls(tls_name)
    fact_definition(toplevel_state, theorem_name).force.retrieveNow
  }

  val get_dependent_thms: MLFunction2[ToplevelState, String, List[String]] =
    compileFunction[ToplevelState, String, List[String]](
      """fn (tls, name) =>
        | let val thy = Toplevel.theory_of tls;
        |     val thm = Global_Theory.get_thms thy name;
        | in
        |     map (fn x => (#1 (#2 x))) (Thm_Deps.thm_deps thy thm)
        | end""".stripMargin
    )
  def get_dependent_theorems(
      tls_name: String,
      theorem_name: String
  ): List[String] = {
    val toplevel_state = retrieve_tls(tls_name)
    // println("Retrieved top level")
    try {
      val dependent_thms = get_dependent_thms(toplevel_state, theorem_name).force.retrieveNow
      return dependent_thms
    } catch {
      case e: IsabelleMLException => {println("Name not found. Trying locales.")}
      case o: Throwable => {println(o)}
    }
    val relevant_locales = locales_defined_in_file(toplevel_state)
    // println(relevant_locales)

    var dep_thms: List[String] = List()
    
    Breaks.breakable {
      for (relevant_locale <- relevant_locales) {
        println("Trying locale: " + relevant_locale)
        val full_name = relevant_locale.trim + '.' + theorem_name
        // println(s"Trying out full name: ${full_name}")
        try {
          val dependent_thms = get_dependent_thms(toplevel_state, full_name).force.retrieveNow
          dep_thms = dependent_thms
          println("This locale works: " + relevant_locale)
          Breaks.break()
        } catch {
          case e: Throwable => {println(e)}
        }
      }
    }
    
    dep_thms
  }

  val get_used_consts: MLFunction2[ToplevelState, String, List[String]] =
    compileFunction[ToplevelState, String, List[String]](
      """fn(tls, inner_syntax) =>
        |let
        |  val term_to_list = fn te =>
        |  let
        |     fun leaves (left $ right) = (leaves left) @ (leaves right)
        |     |   leaves t = [t];
        |     fun filter_out (Const ("_type_constraint_", _)) = false
        |     | filter_out (Const _) = true
        |     | filter_out _ = false;
        |     val all_leaves = leaves te;
        |     val filtered_leaves = filter filter_out all_leaves;
        |     fun remove(_, []) = []
        |       | remove(x, y::l) =
        |         if x = y then
        |           remove(x, l)
        |         else
        |           y::remove(x, l);
        |      fun removeDup [] = []
        |        | removeDup(x::l) = x::removeDup(remove(x, l));
        |      fun string_of_term (Const (s, _)) = s
        |        | string_of_term _ = "";
        |  in
        |      removeDup (map string_of_term filtered_leaves)
        |  end;
        |
        |  val type_to_list = fn ty =>
        |  let
        |    fun type_t (Type ty) = [#1 ty] @ (flat (map type_t (#2 ty)))
        |    | type_t (TFree _) = []
        |    | type_t (TVar _) = [];
        |    fun filter_out_universal_type_symbols symbol =
        |  case symbol of
        |    "fun" => false
        |    | "prop" => false
        |    | "itself" => false
        |    | "dummy" => false
        |    | "proof" => false
        |    | "Pure.proof" => false
        |    | _ => true;
        |  in
        |    filter filter_out_universal_type_symbols (type_t ty)
        |  end;
        |  val ctxt = Toplevel.context_of tls;
        |  val flex = fn str =>
        |   (type_to_list (Syntax.parse_typ ctxt str))
        |   handle _ => (term_to_list (Syntax.parse_term ctxt str));
        |in
        |  flex inner_syntax
        |end""".stripMargin
    )
  def get_all_definitions(
      tls_name: String,
      theorem_string: String
  ): List[String] = {
    val toplevel_state = retrieve_tls(tls_name)
    val quotation_split: List[String] = theorem_string.split('"').toList
    val all_inner_syntax = quotation_split.indices
      .collect { case i if i % 2 == 1 => quotation_split(i) }
      .filter(x => x.nonEmpty)
      .toList
    val all_defs = all_inner_syntax.map(x =>
      get_used_consts(toplevel_state, x).force.retrieveNow
    )
    val deduplicated_all_defs: List[String] = all_defs.flatten
    deduplicated_all_defs.distinct
  }
  // Nasty locales
  val locales_opened_for_state: MLFunction[ToplevelState, List[String]] = compileFunction[ToplevelState, List[String]](
    """fn (tls) => Locale.get_locales (Toplevel.theory_of tls)""".stripMargin
  )

  def locales_defined_in_file(tls: ToplevelState): List[String] = {
    val locales_opened: List[String] = locales_opened_for_state(tls).force.retrieveNow
    locales_opened.filter(_.startsWith(currentTheoryName))
  }

  def local_facts_and_defs_string(tls: ToplevelState): String =
    local_facts_and_defs(tls).force.retrieveNow.distinct
      .map(x => x._1 + "<DEF>" + x._2)
      .mkString("<SEP>")
  def local_facts_and_defs_string(tls_name: String): String = {
    val tls = retrieve_tls(tls_name)
    try {
      local_facts_and_defs_string(tls)
    } catch {
      case e: Throwable => e.toString
    }
  }
  def global_facts_and_defs_string(tls: ToplevelState): String =
    global_facts_and_defs(tls).force.retrieveNow.distinct
      .map(x => x._1 + "<DEF>" + x._2)
      .mkString("<SEP>")
  def global_facts_and_defs_string(tls_name: String): String = {
    val tls = retrieve_tls(tls_name)
    try {
      global_facts_and_defs_string(tls)
    } catch {
      case e: Throwable => e.toString
    }
  }
  def total_facts_and_defs_string(tls: ToplevelState): String = {
    val local_facts = local_facts_and_defs(tls).force.retrieveNow
    val global_facts = global_facts_and_defs(tls).force.retrieveNow
    (local_facts ++ global_facts).distinct
      .map(x => x._1 + "<DEF>" + x._2)
      .mkString("<SEP>")
  }
  def total_facts_and_defs_string(tls_name: String): String = {
    val tls = retrieve_tls(tls_name)
    total_facts_and_defs_string(tls)
  }
  if (debug) println("Checkpoint 4: Theory management")
  val header_read: MLFunction2[String, Position, TheoryHeader] =
    compileFunction[String, Position, TheoryHeader](
      "fn (text,pos) => Thy_Header.read pos text"
    )

  // Find out about the starter string
  // filecontent is the content of thy file to be proved
  private var fileContent: String = Files.readString(Path.of(path_to_file))
  var fileContentCopy: String = fileContent
  if (debug) println("File content: " + fileContent)

  if (debug) println("Checkpoint 6: Starter String")
  private def getStarterString: String = {
    val decoyThy: Theory = Theory("Main")
    for (
      (transition, text) <- parse_text(decoyThy, fileContent).force.retrieveNow
    ) {
      if (
        text.contains("theory") && text.contains(currentTheoryName) && text
          .contains("begin")
      ) {
        return text
      }
    }
    "This is wrong!!!"
  }
  // starter_string is for example "theory Test imports Main HOL.Real begin" 
  val starter_string: String = getStarterString.trim.replaceAll("\n", " ").trim

  // Find out what to import from the current directory
  def getListOfTheoryFiles(dir: File): List[File] = {
    if (dir.exists && dir.isDirectory) {
      var listOfFilesBuffer: ListBuffer[File] = new ListBuffer[File]
      for (f <- dir.listFiles()) {
        if (f.isDirectory) {
          listOfFilesBuffer = listOfFilesBuffer ++ getListOfTheoryFiles(f)
        } else if (f.toString.endsWith(".thy")) {
          listOfFilesBuffer += f
        }
      }
      listOfFilesBuffer.toList
    } else {
      List[File]()
    }
  }

  def sanitiseInDirectoryName(fileName: String): String = {
    fileName.replace("\"", "").split("/").last.split(".thy").head
  }
  if (debug) println("Checkpoint 8: Figure out imports")
  // Figure out what theories to import
  // available_files lists all files in working_dictionary
  val available_files: List[File] = getListOfTheoryFiles(
    new File(working_directory)
  )
  var available_imports_buffer: ListBuffer[String] = new ListBuffer[String]
  for (file_name <- available_files) {
    if (file_name.getName().endsWith(".thy")) {
      available_imports_buffer =
        available_imports_buffer += file_name.getName().split(".thy")(0)
    }
  }
  var available_imports: Set[String] = available_imports_buffer.toSet
  // theoryNames list all theory to be imported e.g., List(Main, HOL.Real)
  val theoryNames: List[String] = starter_string
    .split("imports")(1)
    .split("begin")(0)
    .split(" ")
    .map(_.trim)
    .filter(_.nonEmpty)
    .toList
  var importMap: Map[String, String] = Map()
  for (theory_name <- theoryNames) {
    val sanitisedName = sanitiseInDirectoryName(theory_name)
    if (available_imports(sanitisedName)) {
      importMap += (theory_name.replace("\"", "") -> sanitisedName)
    }
  }
  var top_level_state_map: Map[String, MLValue[ToplevelState]] = Map()
  if (debug) println("Checkpoint 9: func begintheory")
  val theoryManager: TheoryManager = new TheoryManager(
      path_to_isa_bin= path_to_isa_bin,
      wd=working_directory,
    )
  val theoryStarter: TheoryManager.Text =
    TheoryManager.Text(starter_string, setup.workingDirectory.resolve(""))
  var thy1: Theory = theoryManager.beginTheory(theoryStarter)
  if (debug) println("Checkpoint 9_6: Loading theory")
  thy1.await
  if (debug) println("Checkpoint 10: Loading theory finished")

  // setting up Sledgehammer
  // val thy_for_sledgehammer: Theory = Theory("HOL.List")
  val thy_for_sledgehammer = thy1
  val Sledgehammer: String =
    thy_for_sledgehammer.importMLStructureNow("Sledgehammer")
  val Sledgehammer_Commands: String =
    thy_for_sledgehammer.importMLStructureNow("Sledgehammer_Commands")
  val Sledgehammer_Prover: String =
    thy_for_sledgehammer.importMLStructureNow("Sledgehammer_Prover")

  // prove_with_Sledgehammer is mostly identical to check_with_Sledgehammer except for that when the returned Boolean is true, it will
  // also return a non-empty list of Strings, each of which contains executable commands to close the top subgoal. We might need to chop part of
  // the string to get the actual tactic. For example, one of the string may look like "Try this: by blast (0.5 ms)".
  if (debug) println("Checkpoint 11")
  val normal_with_Sledgehammer: MLFunction4[ToplevelState, Theory, List[String], List[String], (Boolean, (String, List[String]))] =
    compileFunction[ToplevelState, Theory, List[String], List[
      String
    ], (Boolean, (String, List[String]))](
      s""" fn (state, thy, adds, dels) =>
            |    let
            |       fun get_refs_and_token_lists (name) = (Facts.named name, []);
            |       val adds_refs_and_token_lists = map get_refs_and_token_lists adds;
            |       val dels_refs_and_token_lists = map get_refs_and_token_lists dels;
            |       val override = {add=adds_refs_and_token_lists,del=dels_refs_and_token_lists,only=false};
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

  var toplevel: ToplevelState = init_toplevel().force.retrieveNow
  if (debug) println("Checkpoint 12")
  def reset_map(): Unit = {
    top_level_state_map = Map()
  }

  def reset_prob(): Unit = {
    thy1 = theoryManager.beginTheory(theoryStarter)
    toplevel = init_toplevel().force.retrieveNow
    reset_map()
  }

  def getFacts(stateString: String): String = {
    var facts: String = ""
    if (stateString.trim.nonEmpty) {
      // Limit the maximum number of local facts to be 5
      for (
        fact <- make_pretty_list_string_list(
          pretty_local_facts(toplevel, false)
        ).retrieveNow.takeRight(5)
      ) {
        facts = facts + fact + "<\\PISASEP>"
      }
    }
    facts
  }

  def getStateString(top_level_state: ToplevelState): String =
    toplevel_string_of_state(top_level_state).force.retrieveNow

  def getStateString: String = getStateString(toplevel)

  def is_done(top_level_state: ToplevelState): Boolean = {
    getProofLevel(top_level_state) == 0
  }

  def getProofLevel(top_level_state: ToplevelState): Int =
    proof_level(top_level_state).retrieveNow

  def getProofLevel: Int = getProofLevel(toplevel)

  def singleTransitionWith10sTimeout(
      single_transition: Transition.T,
      top_level_state: ToplevelState
  ): ToplevelState = {
    command_exception_with_10s_timeout(
      true,
      single_transition,
      top_level_state
    ).retrieveNow.force
  }

  def singleTransitionWith30sTimeout(
      single_transition: Transition.T,
      top_level_state: ToplevelState
  ): ToplevelState = {
    command_exception_with_30s_timeout(
      true,
      single_transition,
      top_level_state
    ).retrieveNow.force
  }

  def singleTransition(
      single_transition: Transition.T,
      top_level_state: ToplevelState
  ): ToplevelState = {
    command_exception(
      true,
      single_transition,
      top_level_state
    ).retrieveNow.force
  }

  def singleTransition(singTransition: Transition.T): String = {
    //    TODO: inlcude global facts
    toplevel = singleTransition(singTransition, toplevel)
    getStateString
  }

  def parseStateAction(isarString: String): String = {
    // Here we directly apply transitions to the theory repeatedly
    // to get the (last_observation, action, observation, reward, done) tuple
    var stateActionTotal: String = ""
    val continue = new Breaks
    // Initialising the state string
    var stateString = getStateString
    var proof_level_number = getProofLevel
    Breaks.breakable {
      for ((transition, text) <- parse_text(thy1, isarString).force.retrieveNow)
        continue.breakable {
          if (text.trim.isEmpty) continue.break()
          else if (text.trim.startsWith("text \\<open>") && text.trim.endsWith("\\<close>")) continue.break()
          else if (text.trim.startsWith("(*") && text.trim.endsWith("*)")) continue.break()
          else {
            stateActionTotal =
              stateActionTotal + (stateString + "<\\STATESEP>" + text.trim + "<\\STATESEP>" + s"$getProofLevel" + "<\\TRANSEP>")
            stateString = singleTransition(transition)
          }
        }
    }
    stateActionTotal
  }

  def parse: String = parseStateAction(fileContent)

  @throws(classOf[IsabelleMLException])
  @throws(classOf[TimeoutException])
  def step(
      isar_string: String,
      top_level_state: ToplevelState,
      timeout_in_millis: Int = 2000
  ): ToplevelState = {
    if (debug) println("Begin step")
    // Normal isabelle business
    var tls_to_return: ToplevelState = clone_tls_scala(top_level_state)
    var stateString: String = ""
    val continue = new Breaks
    if (debug) println("Starting to step")
    val f_st = Future.apply {
      blocking {
        Breaks.breakable {
          if (debug) println("start parsing " + isar_string)
          for ((transition, text) <- parse_text(thy1, isar_string).force.retrieveNow){
            if (debug) println("Transition: " + text)
            continue.breakable {
              if (text.trim.isEmpty) continue.break()
              // println("Small step : " + text)
              if (debug) println("singleTransition: " + timeout_in_millis)
              tls_to_return = if (timeout_in_millis > 10000) {
                singleTransitionWith30sTimeout(transition, tls_to_return)
              } else singleTransitionWith10sTimeout(transition, tls_to_return)
              // println("Applied transition successfully")
            }
          }
        }
      }
      "success"
    }

    // Await for infinite amount of time
    Await.result(f_st, Duration.Inf)
    if (debug) println("Finish step, the current state is " + getStateString(tls_to_return))
    tls_to_return
  }

  def normal_with_hammer(
      top_level_state: ToplevelState,
      added_names: List[String],
      deleted_names: List[String],
      timeout_in_millis: Int = 35000
  ): (Boolean, List[String]) = {
    val f_res: Future[(Boolean, List[String])] = Future.apply {
      val first_result = normal_with_Sledgehammer(
        top_level_state,
        thy1,
        added_names,
        deleted_names
      ).force.retrieveNow
      (first_result._1, first_result._2._2)
    }
    Await.result(f_res, Duration(timeout_in_millis, "millis"))
  }

  if (debug) println("Checkpoint 13: Parse text")
  // return the list of (transition and current step text)
  val transitions_and_texts = parse_text(thy1, fileContent).force.retrieveNow
  var frontier_proceeding_index = 0

  if (debug) println("Checkpoint 14")

  def accumulative_step_to_before_transition_starting(
      isar_string: String
  ): String = {
    val sanitised_isar_string =
      isar_string.trim.replaceAll("\n", " ").replaceAll(" +", " ")
    val (transition, text) = transitions_and_texts(frontier_proceeding_index)
    val sanitised_text = text.trim.replaceAll("\n", " ").replaceAll(" +", " ")
    if (sanitised_text.trim.isEmpty) {
      frontier_proceeding_index += 1
      accumulative_step_to_before_transition_starting(sanitised_isar_string)
    } else if (sanitised_text.trim == sanitised_isar_string) {
      val top_level_proceeding_state = retrieve_tls("default")
      getStateString(top_level_proceeding_state)
    } else {
      frontier_proceeding_index += 1
      val top_level_proceeding_state = retrieve_tls("default")
      val resulting_state: ToplevelState =
        singleTransition(transition, top_level_proceeding_state)
      register_tls("default", resulting_state)
      accumulative_step_to_before_transition_starting(sanitised_isar_string)
    }
  }

  var accumulative_index : Int = 0
  def accumulative_step_before_theorem_starts(theorem_name: String): Unit = {
    val sanitised_theorem_name = theorem_name.trim.replaceAll("\n", " ").replaceAll(" +", " ")
    var found_theorem : Boolean = false
    while (!found_theorem){
      val (transition, text) = transitions_and_texts(accumulative_index)
      val sanitised_text = text.trim.replaceAll("\n", " ").replaceAll(" +", " ")
      if (sanitised_text == sanitised_theorem_name){
        found_theorem = true
      } else {
        // println("Before theorem" + sanitised_text)
        if (sanitised_text.nonEmpty) singleTransition(transition)
        accumulative_index += 1
      }
    }
  }
  def accumulative_step_through_a_theorem: Unit = {
    var proof_finished : Boolean = false
    while (!proof_finished) {
      val (transition, text) = transitions_and_texts(accumulative_index)
      val sanitised_text = text.trim.replaceAll("\n", " ").replaceAll(" +", " ")
      if (sanitised_text.isEmpty) {
        accumulative_index += 1
      }
      else {
        // println("During theorem" + sanitised_text)
        // println("Stepping to: " + sanitised_text)
        singleTransition(transition)
        val proof_level = getProofLevel
        if (proof_level == 0) proof_finished = true
        accumulative_index += 1
      }
    }
  }
  def accumulative_step_to_theorem_end(theorem_name: String) : Unit ={
    accumulative_step_before_theorem_starts(theorem_name)
    accumulative_step_through_a_theorem
  }
  
  /*
  This function is used to get the target state by executing the isar_string from the current state.
  The isar_string can be a long sequence of commands, e.g., "lemma fixes x :: int shows "x ^ 3 = x * x * x \n proof-"
  If after is true, the function will return the state after the transition.
  Otherwise, it will return the state before the transition.  
  */
  def step_to_transition_text(
      isar_string: String,
      after: Boolean = true
  ): String = {
    if (debug) println("Checkpoint 15_1: step to transition text")
    if (debug) println("Cmds to be executed: " + isar_string)
    var stateString: String = ""
    val continue = new Breaks
    Breaks.breakable {
      for (
        (transition, text) <- parse_text(thy1, fileContent).force.retrieveNow
      ) {
        if (debug) println("Transition: " + text)
        continue.breakable {
          if (text.trim.isEmpty) continue.break()
          val trimmed_text =
            text.trim.replaceAll("\n", " ").replaceAll(" +", " ")
          if (trimmed_text == isar_string) {
            if (after) stateString = singleTransition(transition)
            return stateString
          }
          stateString = singleTransition(transition)
        }
      }
    }
    // println("Did not find the text")
    stateString
  }

  // ==================================================================================
  // Give five port for interaction

  def step(isar_string: String): String = {
    toplevel = step(isar_string, toplevel)
    getStateString
  }

  def step_with_30s(isar_string: String): String = {
    toplevel = step(isar_string, toplevel, 30000)
    getStateString
  }

  // todo: using java output
  def prove_by_hammer(timeout_in_millis: Int = 35000): (Boolean, String) = {
    val (ok, tactic) = normal_with_hammer(toplevel, List[String](), List[String](), timeout_in_millis)
    val results: String = tactic.mkString("###")  
    (ok, results)
  }
  // todo: using java output
  // run the theory before proof, and slice the proof
  def parse_theory(isar_string: String): String = {
    if (debug) println("Checkpoint 15_2: parse theory")
    var stateString: String = ""
    val steps = new StringBuffer("")
    var compile: Boolean = true
      for (
        (transition, text) <- parse_text(thy1, fileContent).force.retrieveNow
      ) {
        if (debug) println("Transition: " + text)
        if (compile) {
          stateString = singleTransition(transition)
        } else {
          steps.append("###" + text)
        }
        if (text == "proof-") compile = false
      }
    steps.toString
  }
  
  // reset isabelle and thy to be proved
  def reset_isabelle(path: String): String = {
    path_to_file = path
    currentTheoryName = path_to_file.split("/").last.replace(".thy", "")
    fileContent = Files.readString(Path.of(path_to_file))
    fileContentCopy = fileContent
    thy1 = theoryManager.beginTheory(theoryStarter)
    toplevel = init_toplevel().force.retrieveNow
    reset_map()
    "Reset"
  }

  def exit_isabelle(): String = {
      isabelle.destroy()
      "Destroyed"
  }

  if (debug) println("Checkpoint 15")

  // Manage top level states with the internal map
  def copy_tls: MLValue[ToplevelState] = toplevel.mlValue

  def clone_tls(tls_name: String): Unit =
    top_level_state_map += (tls_name -> copy_tls)

  def clone_tls(old_name: String, new_name: String): Unit =
    top_level_state_map += (new_name -> top_level_state_map(old_name))

  def _clone_tls_scala(tls_scala: ToplevelState): Future[ToplevelState] =
    ToplevelState.converter.retrieve(tls_scala.mlValue)

  def clone_tls_scala(tls_scala: ToplevelState): ToplevelState =
    Await.result(_clone_tls_scala(tls_scala), Duration.Inf)

  def register_tls(name: String, tls: ToplevelState): Unit =
    top_level_state_map += (name -> tls.mlValue)

  def _retrieve_tls(tls_name: String): Future[ToplevelState] =
    ToplevelState.converter.retrieve(top_level_state_map(tls_name))

  def retrieve_tls(tls_name: String): ToplevelState =
    Await.result(_retrieve_tls(tls_name), Duration.Inf)

  def parse_entire_thy: List[String] =
    parse_text(thy1, fileContent).force.retrieveNow.map(_._2)
}