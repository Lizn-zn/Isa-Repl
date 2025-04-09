package RunIsar
  
import org.scalatest.funsuite.AnyFunSuite
import java.nio.file.Paths
import RunIsar.IsaREPL

class ExtractGoalTests extends AnyFunSuite {
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

  // 1. compile the theory env
  val result0: String = isa_repl.compile(""" theory Test imports Main HOL.HOL HOL.Real begin""")

  // test 1
  test("extract notforall goal from Isabelle proof") {
    // create the theorem to be proved
    isa_repl.step("""
      theorem example1: "\<not>(\<forall>n::nat. f (f n) \<noteq> n + 1987)"
        proof 
          assume H: "\<forall>n::nat. f (f n) \<noteq> n + 1987"
      """)
    val result = isa_repl.extract_goal()
    assert(result == "False")
    isa_repl.step("""
      show False 
        sorry
      qed
      """)
  }

  // test 2
  test("extract forall goal from Isabelle proof") {
    // create the theorem to be proved
    isa_repl.step("""
      theorem example2: "\<forall>n::nat. f (f n) = n + 1987"
        proof 
          fix n::nat
      """)
    val result = isa_repl.extract_goal()
    assert(result == "f (f n) = n + 1987")
    isa_repl.step("""
      show "f (f n) = n + 1987" 
        sorry
      qed
      """)
  }

  // test 3
  test("extract ccontr goal from Isabelle proof") {
    // create the theorem to be proved
    isa_repl.step("""
      theorem example3: "(\<exists>n::nat. f (f n) = n + 1987)"
        proof (rule ccontr)
          assume "\<not>(\<exists>n::nat. f (f n) = n + 1987)"
      """)
    val result = isa_repl.extract_goal()
    assert(result == "False")
    isa_repl.step("""
      show False 
        sorry
      qed
      """)
  }

  // test 4
  test("check no_subgoals") {
    isa_repl.step("""
      theorem example4: "False"
        proof-
           show ?thesis
      """)
    val result1 = isa_repl.subgoal_finished()
    assert(result1 == false)
    isa_repl.step("sorry")
    val result2 = isa_repl.subgoal_finished()
    assert(result2 == true)
    isa_repl.step("qed")
  }

}