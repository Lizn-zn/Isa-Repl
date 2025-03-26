package org.isarepl

import py4j.GatewayServer
import RunIsar.IsaREPL
import de.unruh.isabelle.control.IsabelleMLException
import java.nio.file.Paths

class IsaReplApplication {
  val isabelleHome: String = sys.env.getOrElse("ISABELLE_HOME", throw new Exception("ISABELLE_HOME not set"))
  val workingDirectory: String = Paths.get(isabelleHome, "./src/HOL").toAbsolutePath.toString
  
  private var repl: IsaREPL = _
  
  def _initializeRepl(pathToFile: String): Unit = {
    repl = new IsaREPL(
      path_to_isa_bin = isabelleHome,
      path_to_file = pathToFile,
      working_directory = workingDirectory
    )
  }
  
  def _compile(): String = {
    val result = try{
        "True" + "<\\SEP>" + repl.compile()
    } catch {
      case e: IsabelleMLException => 
        "False" + "<\\SEP>" + s"failed for compile the isar environment. Get msg: ${e.getMessage}"
    }
    result
  }

  def _compile(isarProof: String): String = {
    val result = try{
        "True" + "<\\SEP>" + repl.compile(isarProof)
    } catch {
      case e: IsabelleMLException => 
        "False" + "<\\SEP>" + s"failed for compile the isar environment `$isarProof`. Get msg: ${e.getMessage}"
    }
    result
  }
  
  def _step(command: String): String = {
    val result = try{
        "True" + "<\\SEP>" + repl.step(command)
    } catch {
      case e: IsabelleMLException => 
        "False" + "<\\SEP>" + s"failed for prove the goal using the tactic `$command`. Get msg: ${e.getMessage}"
    }
    result
  }

  def _step_with_30s_timeout(command: String): String = {
    val result = try{
        "True" + "<\\SEP>" + repl.step_with_30s(command)
    } catch {
      case e: IsabelleMLException => 
        "False" + "<\\SEP>" + s"failed for prove the goal using the tactic `$command`. Get msg: ${e.getMessage}"
    }
    result
  }

  def _step_without_timeout(command: String): String = {
    val result = try{
        "True" + "<\\SEP>" + repl.step_without_timeout(command)
    } catch {
      case e: IsabelleMLException => 
        "False" + "<\\SEP>" + s"failed for prove the goal using the tactic `$command`. Get msg: ${e.getMessage}"
    }
    result
  }

  def _translate_to_smt(): String = {
    val result = try{
        "True" + "<\\SEP>" + repl.translate_to_smt()
    } catch {
      case e: IsabelleMLException => 
        "False" + "<\\SEP>" + s"failed for translate the goal to smt. Get msg: ${e.getMessage}"
    }
    result
  }

  def _prove_by_hammer(): String = {
    val result = try{
        val (ok, results) = repl.prove_by_hammer()
        if (ok) {
          "True" + "<\\SEP>" + results
        } else {
          "False" + "<\\SEP>" + results
        }
    } catch {
      case e: IsabelleMLException => 
        "False" + "<\\SEP>" + s"failed for prove the goal using hammer. Get msg: ${e.getMessage}"
    }
    result
  }

  def _parse_to_steps(isar_string: String): String = {
    val result = try{
        "True" + "<\\SEP>" + repl.parse_to_steps(isar_string)
    } catch {
      case e: IsabelleMLException => 
        "False" + "<\\SEP>" + s"failed for parse the isar proof to steps. Get msg: ${e.getMessage}"
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
        gateway.shutdown()  // Using correct shutdown method
        println("Server shutdown complete")
      }
    })

    try {
      // Start the gateway server
      gateway.start()
      println(s"Server started on port $port (Press Ctrl+C to stop)")
      println(s"Python connection port: ${gateway.getListeningPort}")

      // Keep the server running until interrupted
      while (!Thread.currentThread.isInterrupted) {
        Thread.sleep(1000)  // Sleep with periodic wakeup
      }
    } catch {
      case e: Exception => 
        println(s"Server error: ${e.getMessage}")
        e.printStackTrace()
        gateway.shutdown()  // Using correct shutdown method
        System.exit(1)
    } finally {
      println("Server process ending")
    }
  }
}
