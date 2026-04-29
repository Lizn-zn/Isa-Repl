package millbuild

import mill.javalib.*

object Deps {

  val logbackClassic = mvn"ch.qos.logback:logback-classic:1.4.14"
  val logbackCore = mvn"ch.qos.logback:logback-core:1.4.14"
  val py4j = mvn"net.sf.py4j:py4j:0.10.9.7;exclude=org.slf4j:*"
  val scalaIsabelle = mvn"de.unruh::scala-isabelle:0.4.3"
  val scalaParserCombinators =
    mvn"org.scala-lang.modules::scala-parser-combinators:2.1.1"
  val scalatest = mvn"org.scalatest::scalatest:3.2.19"
  val classgraph = mvn"io.github.classgraph:classgraph:4.8.184"
}
