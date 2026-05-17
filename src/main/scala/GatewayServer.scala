package org.isarepl

import py4j.GatewayServer
import RunIsar.{IsaREPL, TempFileManager}
import RunIsar.Exceptions.IsabelleMLException
import scala.jdk.CollectionConverters._

import java.nio.file.{Files, Paths, Path}
import java.util
import java.util.concurrent.TimeoutException

class IsaReplApplication {
  var isabelleHome: Option[Path] = IsaREPL.resolveIsabelleHome()
  val workingDirectory: Path = Files.createTempDirectory("IsaREPL")

  private var repl: Option[IsaREPL] = None

  private def requireRepl[A](f: IsaREPL => A): A = {
    repl match {
      case Some(r) => f(r)
      case None =>
        throw new IllegalStateException(
          "REPL not initialized. Call _initializeRepl first."
        )
    }
  }

  def _setIsabelleHome(isabelleHome: String): Unit = {
    if (isabelleHome != null && isabelleHome.nonEmpty) {
      this.isabelleHome = Some(Path.of(isabelleHome))
    } else {
      throw new IllegalArgumentException(
        "Isabelle home path cannot be null or empty"
      )
    }
  }

  // Last-used args for diffing across _initializeRepl calls
  private var lastWorkingDirectory: String = _
  private var lastSession: String = _
  private var lastSessionRoots: List[String] = _

  private def sessionArgsChanged(home: Path, wd: String, sess: String, roots: List[String]): Boolean = {
    repl.isEmpty ||
    home != repl.get.isabelle_home ||
    wd != repl.get.working_directory ||
    sess != repl.get.session ||
    roots != repl.get.session_roots
  }

  private def thyPathChanged(path: String): Boolean = {
    repl.isEmpty || path != repl.get.path_to_thy
  }

  def _initializeRepl(pathToThy: String): String = {
    // This is kept since scala default parameters cannot be called from python through py4j.
    _initializeRepl(
      pathToThy = pathToThy,
      workingDirectory = this.workingDirectory.toString,
      session = "HOL",
      sessionRoots =
        new util.ArrayList[String]() // empty so that no ROOT file is needed.
    )
  }

  def _initializeRepl(
      pathToThy: String,
      workingDirectory: String,
      session: String,
      sessionRoots: util.ArrayList[String]
  ): String = {
    val result =
      try {
        isabelleHome match {
          case None =>
            throw new IllegalStateException(
              "ISABELLE_HOME is not set and isabelle executable not found in PATH"
            )
          case Some(home) =>
            val roots = sessionRoots.asScala.toList
            val wd = workingDirectory
            val sess = session

            if (sessionArgsChanged(home, wd, sess, roots)) {
              // Session args changed (or first call) — full rebuild
              if (repl.isDefined) repl.get.exit_isabelle()
              repl = Some(new IsaREPL(
                isabelle_home = home,
                path_to_thy = pathToThy,
                working_directory = Path.of(wd),
                session = sess,
                session_roots = roots
              ))
            } else if (thyPathChanged(pathToThy)) {
              // Only thy changed — reuse L1, reload L2+L3
              repl.get.loadThy(pathToThy)
            } else {
              // Nothing changed — L3 only
              repl.get.soft_reset()
            }

            lastWorkingDirectory = wd
            lastSession = sess
            lastSessionRoots = roots
        }
        "True" + "<\\SEP>" + "initialize successfully"
      } catch {
        case e: Exception =>
          println(s"Error during initialization: ${e.getMessage}")
          "False" + "<\\SEP>" + s"failed for initialize the isar environment. Get msg: ${e.getMessage}"
      }
    result
  }

  def _resetRepl(): String = {
    val result =
      try {
        requireRepl(_.soft_reset())
        "True" + "<\\SEP>" + "reset successfully"
      } catch {
        case e: Exception =>
          println(s"Error during reset: ${e.getMessage}")
          "False" + "<\\SEP>" + s"failed to reset. Get msg: ${e.getMessage}"
      }
    result
  }

  def _exit(): Unit = {
    try {
      repl.foreach(_.exit_isabelle())
    } catch {
      case e: Exception =>
        println(s"Error during cleanup: ${e.getMessage}")
    }
  }

  def _compile(): String = {
    val result =
      try {
        "True" + "<\\SEP>" + requireRepl(_.compile())
      } catch {
        case e: IsabelleMLException =>
          "False" + "<\\SEP>" + s"failed for compile the isar environment. Get msg: ${e.getMessage}"
      }
    result
  }

  def _compile(isarProof: String): String = {
    val result =
      try {
        "True" + "<\\SEP>" + requireRepl(_.compile(isarProof))
      } catch {
        case e: IsabelleMLException =>
          "False" + "<\\SEP>" + s"failed for compile the isar environment `$isarProof`. Get msg: ${e.getMessage}"
      }
    result
  }

  def _step(command: String): String = {
    val result =
      try {
        "True" + "<\\SEP>" + requireRepl(_.step(command))
      } catch {
        case e: IsabelleMLException =>
          "False" + "<\\SEP>" + s"Failed applying command `$command`. Get msg: ${e.getMessage}"
      }
    result
  }

  def _step_with_30s_timeout(command: String): String = {
    val result =
      try {
        "True" + "<\\SEP>" + requireRepl(_.step_with_30s(command))
      } catch {
        case e: IsabelleMLException =>
          "False" + "<\\SEP>" + s"Failed to apply command `$command`. Get msg: ${e.getMessage}"
        case e: TimeoutException =>
          "False" + "<\\SEP>" + s"Failed to apply command `$command`. Get msg: ${e.getMessage}"
      }
    result
  }

  def _step_without_timeout(command: String): String = {
    val result =
      try {
        "True" + "<\\SEP>" + requireRepl(_.step_without_timeout(command))
      } catch {
        case e: IsabelleMLException =>
          "False" + "<\\SEP>" + s"Failed to apply command `$command`. Get msg: ${e.getMessage}"
      }
    result
  }

  def _translate_to_smt(): String = {
    val result =
      try {
        "True" + "<\\SEP>" + requireRepl(_.translate_to_smt())
      } catch {
        case e: IsabelleMLException =>
          "False" + "<\\SEP>" + s"failed for translate the goal to smt. Get msg: ${e.getMessage}"
      }
    result
  }

  def _prove_by_hammer(): String = {
    val result =
      try {
        val (ok, results) = requireRepl(_.prove_by_hammer())
        if (ok) {
          "True" + "<\\SEP>" + results
        } else {
          "False" + "<\\SEP>" + results
        }
      } catch {
        case e: IsabelleMLException =>
          "False" + "<\\SEP>" + s"failed for prove the goal using hammer. Get msg: ${e.getMessage}"
        case e: TimeoutException =>
          "False" + "<\\SEP>" + s"failed for prove the goal using hammer. Get msg: ${e.getMessage}"
      }
    result
  }

  def _try_close(): String = {
    val result =
      try {
        val (ok, results) = requireRepl(_.try_close())
        if (ok) {
          "True" + "<\\SEP>" + results
        } else {
          "False" + "<\\SEP>" + results
        }
      } catch {
        case e: IsabelleMLException =>
          "False" + "<\\SEP>" + s"failed for try close the goal. Get msg: ${e.getMessage}"
        case e: TimeoutException =>
          "False" + "<\\SEP>" + s"failed for try close the goal. Get msg: ${e.getMessage}"
      }
    result
  }

  def _check_by_nitpick(): String = {
    val result =
      try {
        val (hasCounterexample, message) = requireRepl(_.check_by_nitpick())
        if (hasCounterexample) {
          "True" + "<\\SEP>" + message
        } else {
          "False" + "<\\SEP>" + message
        }
      } catch {
        case e: IsabelleMLException =>
          "False" + "<\\SEP>" + s"failed for check the goal by nitpick. Get msg: ${e.getMessage}"
        case e: TimeoutException =>
          "False" + "<\\SEP>" + s"failed for check the goal by nitpick. Get msg: ${e.getMessage}"
      }
    result
  }

  def _check_by_quickcheck(): String = {
    val result =
      try {
        val (hasCounterexample, message) = requireRepl(_.check_by_quickcheck())
        if (hasCounterexample) {
          "True" + "<\\SEP>" + message
        } else {
          "False" + "<\\SEP>" + message
        }
      } catch {
        case e: IsabelleMLException =>
          "False" + "<\\SEP>" + s"failed for check the goal by quickcheck. Get msg: ${e.getMessage}"
        case e: TimeoutException =>
          "False" + "<\\SEP>" + s"failed for check the goal by quickcheck. Get msg: ${e.getMessage}"
      }
    result
  }

  def _find_theorems(queryPatterns: util.ArrayList[String]): String = {
    val result =
      try {
        val output = requireRepl(_.find_theorems(queryPatterns.asScala.toList))
        "True" + "<\\SEP>" + output
      } catch {
        case e: IsabelleMLException =>
          "False" + "<\\SEP>" + "failed for find_theorems. Get msg: " + e.getMessage
      }
    result
  }

  def _find_theorems(
      queryPatterns: util.ArrayList[String],
      limit: Int,
      removeDuplicates: Boolean
  ): String = {
    val result =
      try {
        val output = requireRepl(_.find_theorems(
          queryPatterns.asScala.toList,
          limit,
          removeDuplicates
        ))
        "True" + "<\\SEP>" + output
      } catch {
        case e: IsabelleMLException =>
          "False" + "<\\SEP>" + "failed for find_theorems. Get msg: " + e.getMessage
      }
    result
  }

  def _parse_to_steps(isar_string: String): String = {
    val result =
      try {
        "True" + "<\\SEP>" + requireRepl(_.parse_to_steps(isar_string))
      } catch {
        case e: IsabelleMLException =>
          "False" + "<\\SEP>" + s"failed for parse the isar proof to steps. Get msg: ${e.getMessage}"
      }
    result
  }

  def _extract_vars(): String = {
    val result =
      try {
        val vars = requireRepl(_.extract_vars())
        "True" + "<\\SEP>" + vars.mkString("<\\SEP>")
      } catch {
        case e: IsabelleMLException =>
          "False" + "<\\SEP>" + s"failed for extract the vars. Get msg: ${e.getMessage}"
      }
    result
  }

  def _extract_assms(): String = {
    val result =
      try {
        val assms = requireRepl(_.extract_assms())
        "True" + "<\\SEP>" + assms.mkString("<\\SEP>")
      } catch {
        case e: IsabelleMLException =>
          "False" + "<\\SEP>" + s"failed for extract the assms. Get msg: ${e.getMessage}"
      }
    result
  }

  def _extract_goal(): String = {
    val result =
      try {
        val goal = requireRepl(_.extract_goal())
        "True" + "<\\SEP>" + goal
      } catch {
        case e: IsabelleMLException =>
          "False" + "<\\SEP>" + s"failed for extract the goal. Get msg: ${e.getMessage}"
      }
    result
  }

  def _extract_thm_deps(isarString: String): String = {
    val result =
      try {
        val dep_thm_lst = requireRepl(_.extract_thm_deps(isarString))
        "True" + "<\\SEP>" + dep_thm_lst.mkString("<\\SEP>")
      } catch {
        case e: IsabelleMLException =>
          "False" + "<\\SEP>" + s"failed for extract dependent theorems. Get msg: ${e.getMessage}"
      }
    result
  }

  def _extract_hammer_facts(): String = {
    val result =
      try {
        val facts = requireRepl(_.extract_hammer_facts())
        "True" + "<\\SEP>" + facts
      } catch {
        case e: IsabelleMLException =>
          "False" + "<\\SEP>" + s"failed for extract facts. Get msg: ${e.getMessage}"
      }
    result
  }

  def _extract_thm_deps_with_thy_names(isarString: String): String = {
    val result =
      try {
        val dep_thm_lst = requireRepl(_.extract_thm_deps_with_thy_names(isarString))
        "True" + "<\\SEP>" + dep_thm_lst.mkString("<\\SEP>")
      } catch {
        case e: IsabelleMLException =>
          "False" + "<\\SEP>" + s"failed for extract dependent theorems. Get msg: ${e.getMessage}"
      }
    result
  }

  def _extract_hammer_facts_with_thy_names(): String = {
    val result =
      try {
        val facts = requireRepl(_.extract_hammer_facts_with_thy_names())
        "True" + "<\\SEP>" + facts
      } catch {
        case e: IsabelleMLException =>
          "False" + "<\\SEP>" + s"failed for extract facts. Get msg: ${e.getMessage}"
      }
    result
  }

  def _extract_hammer_facts_with_thy_names(filter: String): String = {
    val result =
      try {
        val facts = requireRepl(_.extract_hammer_facts_with_thy_names(filter))
        "True" + "<\\SEP>" + facts
      } catch {
        case e: IsabelleMLException =>
          "False" + "<\\SEP>" + s"failed for extract facts. Get msg: ${e.getMessage}"
      }
    result
  }

  def _extract_hammer_facts_with_thy_names(
      filter: String,
      adds: List[String],
      dels: List[String]
  ): String = {
    val result =
      try {
        val facts = requireRepl(_.extract_hammer_facts_with_thy_names())
        "True" + "<\\SEP>" + facts
      } catch {
        case e: IsabelleMLException =>
          "False" + "<\\SEP>" + s"failed for extract facts. Get msg: ${e.getMessage}"
      }
    result
  }

  def _extract_thms_defined_in_parent(): String = {
    val result =
      try {
        val facts = requireRepl(_.extract_thm_defined_in_parent()).mkString("<\\SEP>")
        "True" + "<\\SEP>" + facts
      } catch {
        case e: IsabelleMLException =>
          "False" + "<\\SEP>" + s"failed for extract theorems from parent. Get msg: ${e.getMessage}"
      }
    result
  }

  def _mash_state_relearn(): String = {
    val result =
      try {
        requireRepl(_.mash_state_relearn())
        "True"
      } catch {
        case e: IsabelleMLException =>
          "False" + "<\\SEP>" + s"failed to relearn from Isar proofs. Get msg: ${e.getMessage}"
      }
    result
  }

  def _clone_tls(tls_name: String): String = {
    val result =
      try {
        requireRepl(_.clone_tls(tls_name))
        "True"
      } catch {
        case e: IsabelleMLException =>
          "False" + "<\\SEP>" + s"failed for extract the goal. Get msg: ${e.getMessage}"
      }
    result
  }

  def _remove_tls(tls_name: String): String = {
    val result =
      try {
        requireRepl(_.remove_tls(tls_name))
        "True"
      } catch {
        case e: IsabelleMLException =>
          "False" + "<\\SEP>" + s"failed for remove the tls. Get msg: ${e.getMessage}"
      }
    result
  }

  def _focus_tls(tls_name: String): String = {
    val result =
      try {
        requireRepl(_.focus_tls(tls_name))
        "True"
      } catch {
        case e: IsabelleMLException =>
          "False" + "<\\SEP>" + s"failed for extract the goal. Get msg: ${e.getMessage}"
      }
    result
  }

  def _proof_finished(): String = {
    val result =
      try {
        if (requireRepl(_.check_no_subgoals())) {
          "True" + "<\\SEP>" + "no additional messages"
        } else {
          "False" + "<\\SEP>" + "no additional messages"
        }
      } catch {
        case e: IsabelleMLException =>
          "False" + "<\\SEP>" + s"failed for check if the subgoal is finished. Get msg: ${e.getMessage}"
      }
    result
  }

}

object IsaReplGatewayServer {
  def main(args: Array[String]): Unit = {
    // Parse port from command line arguments (default: 25333)
    val port = if (args.length > 0) args(0).toInt else 25333

    // Initialize GatewayServer instance with just the port
    val app = new IsaReplApplication()
    val gateway = new GatewayServer(app, port)

    // Register shutdown hook for graceful termination
    Runtime.getRuntime.addShutdownHook(new Thread {
      override def run(): Unit = {
        println("\nReceived shutdown signal - terminating gracefully...")
        try {
          app._exit()
          gateway.shutdown()
          println("Server shutdown complete")
        } catch {
          case e: Exception =>
            println(s"Error during shutdown: ${e.getMessage}")
            e.printStackTrace()
        }
      }
    })

    try {
      // Start the gateway server
      gateway.start()
      println(s"Server started on port $port (Press Ctrl+C to stop)")
      println(s"Python connection port: ${gateway.getListeningPort}")

      // Keep the server running until interrupted
      while (!Thread.currentThread.isInterrupted) {
        Thread.sleep(1000) // Sleep with periodic wakeup
      }
    } catch {
      case e: Exception =>
        println(s"Server error: ${e.getMessage}")
        e.printStackTrace()
        app._exit()
        gateway.shutdown()
        System.exit(1)
    } finally {
      println("Server process ending")
    }
  }
}
