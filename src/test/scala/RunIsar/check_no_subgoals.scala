package RunIsar

import org.scalatest.funsuite.AnyFunSuite
import java.nio.file.{Path, Paths}
import RunIsar.IsaREPL

class CheckNoSubgoalsTests extends AnyFunSuite {
  val isabelle_home: Path = IsaREPL.resolveIsabelleHome() match {
    case Some(path) => path
    case None =>
      throw new Exception(
        "ISABELLE_HOME not set and isabelle executable not found in PATH"
      )
  }

  val path_to_thy: String =
    Paths.get("python-test/Test.thy").toAbsolutePath.toString
  val working_directory: Path =
    Paths.get("python-test").toAbsolutePath

  val isa_repl = new IsaREPL(
    isabelle_home = isabelle_home,
    path_to_thy = path_to_thy,
    working_directory = working_directory,
    debug = true
  )

  // compile theory header and load definitions from Seq.thy
  val result0: String =
    isa_repl.compile("theory Test imports Main begin")

  isa_repl.step("datatype 'a seq = Empty | Seq 'a \"'a seq\"")
  isa_repl.step(
    "fun conc :: \"'a seq => 'a seq => 'a seq\" where \"conc Empty ys = ys\" | \"conc (Seq x xs) ys = Seq x (conc xs ys)\""
  )
  isa_repl.step(
    "fun reverse :: \"'a seq => 'a seq\" where \"reverse Empty = Empty\" | \"reverse (Seq x xs) = conc (reverse xs) (Seq x Empty)\""
  )

  // test 1: after definitions, no proof should be active
  test("check_no_subgoals returns true when no proof is active (theory mode)") {
    assert(isa_repl.check_no_subgoals() == true)
  }

  // test 2: a bare lemma statement enters proof mode with an open subgoal
  test("check_no_subgoals returns false when a lemma has an open subgoal") {
    isa_repl.step("lemma conc_empty: \"conc xs Empty = xs\"")
    assert(isa_repl.check_no_subgoals() == false)
  }

  // test 3: by closes the subgoal and returns to theory mode
  test("check_no_subgoals returns true after by closes the subgoal") {
    isa_repl.step("  by (induct xs) simp_all")
    assert(isa_repl.check_no_subgoals() == true)
  }
}
