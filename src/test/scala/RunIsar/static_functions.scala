package RunIsar

import org.scalatest.funsuite.AnyFunSuite
import RunIsar.IsaREPL

class StaticFunctionsTests extends AnyFunSuite {
  // test 1
  test("Recode to Unicode") {
    val transformed_string = IsaREPL.isabelle2unicode(
      """lemma bin_eq_iff: "x = y \<longleftrightarrow> (\<forall>n. (bit :: int \<Rightarrow> nat \<Rightarrow> bool) x n = (bit :: int \<Rightarrow> nat \<Rightarrow> bool) y n)"
  by (metis bit_eq_iff)"""
    )
    assert(
      transformed_string == """lemma bin_eq_iff: "x = y ⟷ (∀n. (bit :: int ⇒ nat ⇒ bool) x n = (bit :: int ⇒ nat ⇒ bool) y n)"
  by (metis bit_eq_iff)"""
    )
  }
  // test 2
  test("Encode to Isabelle-UTF8") {
    val transformed_string = IsaREPL.unicode2isabelle(
      """lemma bin_eq_iff: "x = y ⟷ (∀n. (bit :: int ⇒ nat ⇒ bool) x n = (bit :: int ⇒ nat ⇒ bool) y n)"
  by (metis bit_eq_iff)"""
    )
    assert(
      transformed_string == """lemma bin_eq_iff: "x = y \<longleftrightarrow> (\<forall>n. (bit :: int \<Rightarrow> nat \<Rightarrow> bool) x n = (bit :: int \<Rightarrow> nat \<Rightarrow> bool) y n)"
  by (metis bit_eq_iff)"""
    )
  }
}
