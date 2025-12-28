package millbuild

import mill.javalib.*

object Deps {

  val logbackClassic = mvn"ch.qos.logback:logback-classic:1.1.3"
  val py4j = mvn"net.sf.py4j:py4j:0.10.9.7;exclude=org.slf4j:*"
  val scalaIsabelle = mvn"de.unruh::scala-isabelle:master-SNAPSHOT"
  val scalaParserCombinators =
    mvn"org.scala-lang.modules::scala-parser-combinators:2.1.1"
  val scalatest = mvn"org.scalatest::scalatest:3.2.19"
}
