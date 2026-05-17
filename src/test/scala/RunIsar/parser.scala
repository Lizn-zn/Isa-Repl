package RunIsar

import org.scalatest.funsuite.AnyFunSuite
import java.nio.file.{Path, Paths}
import RunIsar.IsaREPL

class ParserTests extends AnyFunSuite {
  // get the value of isabelleHome_str from env variable ISABELLE_HOME. If not set, raise an error
  val isabelleHome_str: String = sys.env.getOrElse(
    "ISABELLE_HOME",
    throw new Exception("ISABELLE_HOME not set")
  )
  val isabelle_home: Path = Path.of(isabelleHome_str)

  val path_to_thy: String =
    Paths.get("python-test/Test.thy").toAbsolutePath.toString
  val working_directory: Path =
    Paths.get("python-test").toAbsolutePath
  val isa_repl = new IsaREPL(
    isabelle_home = isabelle_home,
    path_to_thy = path_to_thy,
    working_directory = working_directory,
    debug = false
  )

  // 1. compile the theory env
  val result0: String =
    isa_repl.compile(""" theory Test imports Main HOL.HOL HOL.Real begin""")

  // test 1
  test("parse the theorem to steps") {
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
    val steps: List[String] = result1.split("<\\\\SEP>").toList // NOTE: the separator is "<\\\\SEP>" cos "\\S" is a special character in regex, so we need to escape it with another backslash. And in Scala string, we need to escape the backslash with another backslash, so we end up with "<\\\\SEP>"
    assert(
      steps == List(
        "",
        "lemma fixes x :: int shows \"x ^ 3 = x * x * x\"",
        "proof-",
        "have eq1: \"x ^ 2 = x * x\"",
        "by auto",
        "have eq2: \"x ^ 3 = x ^ 2 * x\"",
        "by auto",
        "show ?thesis",
        "by (simp add: eq1 eq2)"
      )
    )
  }

  // test 2: simple private lemma — single step, no split
  test("parse private lemma: before_command should not split") {
    val theorem_string = "private lemma \"f (-2) = f (13 + 1 :: 'a::len word)\""
    val result1: String = isa_repl.parse_to_steps(theorem_string)
    val steps: List[String] = result1.split("<\\\\SEP>").toList
    val nonEmptySteps = steps.filter(_.trim.nonEmpty)
    assert(
      nonEmptySteps.length == 1,
      s"Expected 1 non-empty step for 'private lemma', got ${nonEmptySteps.length}: ${nonEmptySteps.mkString(" | ")}"
    )
    assert(
      nonEmptySteps.head.contains("private") && nonEmptySteps.head.contains("lemma"),
      s"Expected step to contain both 'private' and 'lemma', got: '${nonEmptySteps.head}'"
    )
  }

  // test 3: multi-line private lemma with apply/oops
  test("parse multi-line private lemma with apply/oops") {
    val theorem_string = """private lemma "f (-2) = f (13 + 1 :: 'a::len word)"
        |  apply simp
        |  oops""".stripMargin
    val result1: String = isa_repl.parse_to_steps(theorem_string)
    val steps: List[String] = result1.split("<\\\\SEP>").toList
    val nonEmptySteps = steps.filter(_.trim.nonEmpty)
    // First step must contain BOTH private and lemma
    assert(
      nonEmptySteps.head.contains("private") && nonEmptySteps.head.contains("lemma"),
      s"Step 0 should contain 'private lemma', got: '${nonEmptySteps.head}'"
    )
  }

  // test 4: regression test for the bug — private lemma in context
  test("parse private lemma in context: private must not be a separate step") {
    val theorem_string = """lemma "f (7 :: 2 word) = f 3" by simp
        |private lemma "f (-2) = f (13 + 1 :: 'a::len word)"
        |  apply simp
        |  oops
        |lemma "f 7 = f (3 :: 2 word)" by simp""".stripMargin
    val result1: String = isa_repl.parse_to_steps(theorem_string)
    val steps: List[String] = result1.split("<\\\\SEP>").toList
    val nonEmptySteps = steps.filter(_.trim.nonEmpty)
    // Find the step containing "private"
    val privStep = nonEmptySteps.find(s => s.contains("private")).getOrElse("")
    // Regression: "private" must be in the same step as "lemma", not isolated
    assert(
      privStep.contains("lemma"),
      s"BUG: 'private' found in step without 'lemma': '${privStep.take(80)}'"
    )
    // Verify no isolated "private" step exists
    val isolatedPrivate = nonEmptySteps.exists { s =>
      val t = s.trim
      t == "private" || t == "private " || t == " private"
    }
    assert(!isolatedPrivate, "BUG: found isolated 'private' step")
  }

}
