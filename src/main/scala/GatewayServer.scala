package org.isarepl

import py4j.GatewayServer
import RunIsar.{IsaREPL, IsabelleMLException}

import java.nio.file.Paths
import java.util
import java.util.concurrent.TimeoutException
import RunIsar.TempFileManager

class IsaReplApplication {
  val isabelleHome: String = sys.env.getOrElse(
    "ISABELLE_HOME",
    throw new Exception("ISABELLE_HOME not set")
  )
  val workingDirectory: String =
    Paths.get(isabelleHome, "./src/HOL").toAbsolutePath.toString

  private var repl: IsaREPL = _

  def _initializeRepl(pathToFile: String): Unit = {
    repl = new IsaREPL(
      path_to_isa_bin = isabelleHome,
      path_to_file = pathToFile,
      working_directory = workingDirectory
    )
  }

  def _initializeRepl(
      pathToFile: String,
      logic: String,
      sessionRoots: util.ArrayList[String],
      workingDirectory: String
  ): Unit = {
    repl = new IsaREPL(
      path_to_isa_bin = isabelleHome,
      path_to_file = pathToFile,
      working_directory = workingDirectory,
      logic = logic,
      session_roots = sessionRoots.asScala.toList
    )
  }

  def _resetRepl(pathToFile: String): Unit = {
    val msg = repl.reset_isabelle(pathToFile)
    if (msg != "Reset") {
      _initializeRepl(pathToFile)
    }
  }

  def _cleanup(): Unit = {
    try {
      if (repl != null) {
        repl.exit_isabelle()
      }
      TempFileManager.cleanupAll()
    } catch {
      case e: Exception => 
        println(s"Error during cleanup: ${e.getMessage}")
    }
  }
  
  def _compile(): String = {
    val result =
      try {
        "True" + "<\\SEP>" + repl.compile()
      } catch {
        case e: IsabelleMLException =>
          "False" + "<\\SEP>" + s"failed for compile the isar environment. Get msg: ${e.getMessage}"
      }
    result
  }

  def _compile(isarProof: String): String = {
    val result =
      try {
        "True" + "<\\SEP>" + repl.compile(isarProof)
      } catch {
        case e: IsabelleMLException =>
          "False" + "<\\SEP>" + s"failed for compile the isar environment `$isarProof`. Get msg: ${e.getMessage}"
      }
    result
  }

  def _step(command: String): String = {
    val result =
      try {
        "True" + "<\\SEP>" + repl.step(command)
      } catch {
        case e: IsabelleMLException =>
          "False" + "<\\SEP>" + s"failed for prove the goal using the tactic `$command`. Get msg: ${e.getMessage}"
      }
    result
  }

  def _step_with_30s_timeout(command: String): String = {
    val result =
      try {
        "True" + "<\\SEP>" + repl.step_with_30s(command)
    } catch {
      case e: IsabelleMLException => 
        "False" + "<\\SEP>" + s"failed for prove the goal using the tactic `$command`. Get msg: ${e.getMessage}"
      case e: TimeoutException =>
        "False" + "<\\SEP>" + s"failed for prove the goal using the tactic `$command`. Get msg: ${e.getMessage}"
    }
    result
  }

  def _step_without_timeout(command: String): String = {
    val result =
      try {
        "True" + "<\\SEP>" + repl.step_without_timeout(command)
      } catch {
        case e: IsabelleMLException =>
          "False" + "<\\SEP>" + s"failed for prove the goal using the tactic `$command`. Get msg: ${e.getMessage}"
      }
    result
  }

  def _translate_to_smt(): String = {
    val result =
      try {
        "True" + "<\\SEP>" + repl.translate_to_smt()
      } catch {
        case e: IsabelleMLException =>
          "False" + "<\\SEP>" + s"failed for translate the goal to smt. Get msg: ${e.getMessage}"
      }
    result
  }

  def _prove_by_hammer(): String = {
    val result =
      try {
        val (ok, results) = repl.prove_by_hammer()
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
    val result = try{
        val (ok, results) = repl.try_close()
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

  def _parse_to_steps(isar_string: String): String = {
    val result =
      try {
        "True" + "<\\SEP>" + repl.parse_to_steps(isar_string)
      } catch {
        case e: IsabelleMLException =>
          "False" + "<\\SEP>" + s"failed for parse the isar proof to steps. Get msg: ${e.getMessage}"
      }
    result
  }

  def _extract_vars(): String = {
    val result =
      try {
        val vars = repl.extract_vars()
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
        val assms = repl.extract_assms()
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
        val goal = repl.extract_goal()
        "True" + "<\\SEP>" + goal
      } catch {
        case e: IsabelleMLException =>
          "False" + "<\\SEP>" + s"failed for extract the goal. Get msg: ${e.getMessage}"
      }
    result
  }

  def _extract_thm_deps(isarString: String): List[String] = {
    repl.extract_thm_deps(isarString)
  }

  def _extract_hammer_facts(): String = {
    repl.extract_hammer_facts()
  }

  def _clone_tls(tls_name: String): String = {
    val result =
      try {
        repl.clone_tls(tls_name)
        "True"
    } catch {
      case e: IsabelleMLException => 
        "False" + "<\\SEP>" + s"failed for extract the goal. Get msg: ${e.getMessage}"
    }
    result
  }

  def _remove_tls(tls_name: String): String = {
    val result = try {
        repl.remove_tls(tls_name)
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
        repl.focus_tls(tls_name)
        "True"
      } catch {
        case e: IsabelleMLException =>
          "False" + "<\\SEP>" + s"failed for extract the goal. Get msg: ${e.getMessage}"
      }
    result
  }

  def _subgoal_finished(): String = {
    val result =
      try {
        val res = repl.subgoal_finished()
        if (res == true) {
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
          app._cleanup()
          gateway.shutdown()
          println("Server shutdown complete")
        } catch {
          case e: Exception =>
            println(s"Error during shutdown: ${e.getMessage}")
            e.printStackTrace()
        }
        try {
          app._cleanup()
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
        app._cleanup()
        gateway.shutdown()
        app._cleanup()
        gateway.shutdown()
        System.exit(1)
    } finally {
      println("Server process ending")
    }
  }
}
