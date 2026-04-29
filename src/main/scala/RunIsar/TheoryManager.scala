package RunIsar

import java.nio.file.{Path, Paths}
import _root_.java.nio.file.{Files, Path}
import _root_.java.io.File
import scala.collection.mutable.ListBuffer

import de.unruh.isabelle.control.{
  Isabelle,
  OperationCollection,
  IsabelleMLException
}
import de.unruh.isabelle.pure.{Position, Theory, TheoryHeader, ToplevelState}
import de.unruh.isabelle.mlvalue.MLValue.{compileFunction, compileFunction0}
import de.unruh.isabelle.mlvalue.{
  MLFunction,
  MLFunction0,
  MLFunction2,
  MLFunction3,
  Version
}

import TheoryManager.{Heap, Source, Text}
import TheoryManager.Ops

// Implicits
import de.unruh.isabelle.mlvalue.Implicits._
import de.unruh.isabelle.pure.Implicits._
import scala.concurrent.ExecutionContext.Implicits.global

/*
For TheoryManager
  path_to_thy: the file to be proved
  working_directory: the directory containing all .thy files to be imported
  logic: the logic to be used
  sessionRoots: the session roots to be used
  debug: whether to print debug information
 */

class TheoryManager(
    val isabelle_home: Path,
    val path_to_thy: String,
    val working_directory: Path,
    val session: String,
    val sessionRoots: List[String],
    implicit val isabelle: Isabelle,
    val debug: Boolean = false
) {

  if (working_directory.startsWith(isabelle_home)) {
    throw new Exception(
      "working_directory should not be set in the same directory as isabelleHome"
    )
  }

  val currentTheoryName: String =
    path_to_thy.split("/").last.replace(".thy", "")

  // Find out about the starter string
  // filecontent is the content of thy file to be proved
  private var fileContent: String = Files.readString(Path.of(path_to_thy))
  var fileContentCopy: String = fileContent
  if (debug) println("File content: " + fileContent)

  val command_exception
      : MLFunction3[Boolean, Transition.T, ToplevelState, ToplevelState] =
    compileFunction[Boolean, Transition.T, ToplevelState, ToplevelState](
      "fn (int, tr, st) => Toplevel.command_exception int tr st"
    )
  lazy val init_toplevel: MLFunction0[ToplevelState] =
    if (Version.from2023)
      compileFunction0[ToplevelState]("fn _ => Toplevel.make_state NONE")
    else
      compileFunction0[ToplevelState]("Toplevel.init_toplevel")
  if (debug) println("Checkpoint 4: Theory management")
  val header_read: MLFunction2[String, Position, TheoryHeader] =
    compileFunction[String, Position, TheoryHeader](
      "fn (text,pos) => Thy_Header.read pos text"
    )
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
  val toplevel_end_theory: MLFunction[ToplevelState, Theory] =
    compileFunction[ToplevelState, Theory]("Toplevel.end_theory Position.none")

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
  val theoryStarter: TheoryManager.Text =
    TheoryManager.Text(starter_string, working_directory.resolve(""))

  /** Normalizes an import pattern string by removing surrounding quotes (if
    * present), extracting the theory name if it is a path.
    *
    * @param import_string
    *   The import string, possibly quoted and containing a file path.
    * @return
    *   The normalized import pattern, which is either the theory name or a
    *   'SessionA.TheoryB' format.
    */
  def normalizeImportPattern(import_string: String): String = {
    val p = if (import_string.startsWith("\"") && import_string.endsWith("\"")) {
      import_string.substring(1, import_string.length - 1)
    } else import_string
    p.split("/").last.stripSuffix(".thy")
  }

  def getTheorySource(name: String): Source = Heap(name)
  def getTheory(source: Source)(implicit isabelle: Isabelle): Theory =
    source match {
      case Heap(name) => Theory(name)
      case Text(text, path, position) =>
        var toplevel = init_toplevel().force.retrieveNow
        var thy0 = beginTheory(source)
        for ((transition, text) <- parse_text(thy0, text).force.retrieveNow) {
          toplevel =
            command_exception(true, transition, toplevel).retrieveNow.force
        }
        toplevel_end_theory(toplevel).retrieveNow.force
    }

  def beginTheory(
      source: Source = theoryStarter
  )(implicit isabelle: Isabelle): Theory = {
    if (debug) println("Checkpoint 9_1")
    val header = getHeader(source)
    if (debug) println("Checkpoint 9_2")
    val masterDir = source.path
    if (debug) println("Checkpoint 9_3")
    val registers: ListBuffer[String] = new ListBuffer[String]()
    if (debug) println("Checkpoint 9_4")
    for (theory_name <- header.imports.map(normalizeImportPattern)) {
      // If n is not of the form "Main", "Pure" or "A.B", prepend the session name
      if (
        theory_name == "Main" || theory_name == "Pure" || theory_name.contains(".")
      ) {
        registers += theory_name
      } else {
        registers += s"$session.$theory_name"
      }
    }
    if (debug) println("Checkpoint 9_5")
    try {
      Ops
        .begin_theory(masterDir, header, registers.toList.map(Theory.apply))
        .force
        .retrieveNow
    } catch {
      case e: IsabelleMLException =>
        throw e
    }
  }
  def getHeader(source: Source)(implicit isabelle: Isabelle): TheoryHeader =
    source match {
      case Text(text, path, position) =>
        Ops.header_read(text, position).retrieveNow
    }
}

object TheoryManager extends OperationCollection {
  trait Source { def path: Path }
  case class Heap(name: String) extends Source {
    override def path: Path = Paths.get("INVALID")
  }
  case class File(path: Path) extends Source
  case class Text(text: String, path: Path, position: Position) extends Source
  object Text {
    def apply(text: String, path: Path)(implicit isabelle: Isabelle): Text =
      new Text(text, path, Position.none)
  }

  // noinspection TypeAnnotation
  protected final class Ops(implicit val isabelle: Isabelle) {
    val header_read = compileFunction[String, Position, TheoryHeader](
      "fn (text,pos) => Thy_Header.read pos text"
    )
    val begin_theory = compileFunction[Path, TheoryHeader, List[
      Theory
    ], Theory](
      "fn (path, header, parents) => Resources.begin_theory path header parents"
    )
  }

  override protected def newOps(implicit isabelle: Isabelle): Ops = {
    new Ops()
  }
}
