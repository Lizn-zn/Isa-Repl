//| mill-version: 1.0.6
package build

import mill.javalib.*
import mill.javalib.publish.*
import mill.scalalib.*
import millbuild.*

object `package` extends PublishModule with SbtModule {

  def mvnDeps = super.mvnDeps() ++ Seq(
    Deps.scalaParserCombinators,
    Deps.py4j,
    Deps.logbackClassic,
    Deps.logbackCore,
    Deps.scalaIsabelle,
    Deps.classgraph
  )

  def artifactName = "isa-repl"

  def scalaVersion = "2.13.14"

  def pomSettings = PomSettings(
    "Isa-Repl",
    "ch.epfl.scala",
    "",
    Seq(),
    VersionControl(None, None, None, None),
    Seq()
  )

  def publishVersion = "1.0"

  def repositories = super.repositories() ++ Seq(
    "https://oss.sonatype.org/content/repositories/snapshots",
    "https://s01.oss.sonatype.org/content/repositories/snapshots"
  )

  object test extends SbtTests with TestModule.ScalaTest {

    def mvnDeps = super.mvnDeps() ++ Seq(Deps.scalatest)

    // Configure ScalaTest to show output
    def testArgs = Seq("-o")

    def testParallelism = true

    def testSandboxWorkingDir = false

  }

}
