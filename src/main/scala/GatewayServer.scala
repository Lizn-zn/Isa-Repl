package org.isarepl

import py4j.GatewayServer
import RunIsar.IsaREPL
import java.nio.file.Paths

class IsaReplApplication {
  val isabelleHome: String = sys.env.getOrElse("ISABELLE_HOME", throw new Exception("ISABELLE_HOME not set"))
  val workingDirectory: String = Paths.get(isabelleHome, "./src/HOL").toAbsolutePath.toString
  
  private var repl: IsaREPL = _
  
  def initializeRepl(pathToFile: String): Unit = {
    repl = new IsaREPL(
      path_to_isa_bin = isabelleHome,
      path_to_file = pathToFile,
      working_directory = workingDirectory
    )
  }
  
  def compile(): String = {
    repl.compile()
  }

  def compile(isarProof: String): String = {
    repl.compile(isarProof)
  }
  
  def step(command: String): String = {
    repl.step(command)
  }

  def step_with_30s_timeout(command: String): String = {
    repl.step_with_30s(command)
  }

  def step_without_timeout(command: String): String = {
    repl.step_without_timeout(command)
  }

  def translate_to_smt(): String = {
    repl.translate_to_smt()
  }

  def prove_by_hammer(): (Boolean, String) = {
    repl.prove_by_hammer()
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
