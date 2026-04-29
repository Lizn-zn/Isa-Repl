package RunIsar

import java.nio.file.{Files, Paths, StandardCopyOption, FileSystem, FileSystems}
import scala.jdk.CollectionConverters._
import java.io.{File, IOException}
import java.net.URI
import scala.util.Using
import scala.collection.mutable.Set
import io.github.classgraph.ClassGraph

/**
 * Manages temporary file operations including copying resources from JAR/filesystem.
 * Handles both development and packaged JAR environments.
 */
class TempFileManager {
  private val tempDirs = Set[File]()

  // Add shutdown hook for cleanup
  Runtime.getRuntime.addShutdownHook(new Thread {
    override def run(): Unit = {
      cleanupAll()
    }
  })

  /**
   * Clean up all temporary directories
   */
  def cleanupAll(): Unit = {
    tempDirs.foreach { dir =>
      try {
        if (dir.exists()) {
          Files.walk(dir.toPath)
            .sorted(java.util.Comparator.reverseOrder())
            .forEach { path =>
              try {
                Files.deleteIfExists(path)
              } catch {
                case e: IOException => 
                  println(s"Warning: Could not delete ${path}: ${e.getMessage}")
              }
            }
        }
      } catch {
        case e: Exception => 
          println(s"Error cleaning up ${dir}: ${e.getMessage}")
      }
    }
    tempDirs.clear()
  }

  /**
   * Clean up a specific temporary directory
   */
  def cleanup(dir: File): Unit = {
    try {
      if (dir.exists()) {
        Files.walk(dir.toPath)
          .sorted(java.util.Comparator.reverseOrder())
          .forEach { path =>
            try {
              Files.deleteIfExists(path)
            } catch {
              case e: IOException => 
                println(s"Warning: Could not delete ${path}: ${e.getMessage}")
            }
          }
      }
      tempDirs -= dir
    } catch {
      case e: Exception => 
        println(s"Error cleaning up ${dir}: ${e.getMessage}")
    }
  }

  /** Copies a resource directory to target location.
    * @param sourcePath
    *   Resource path relative to "src/main/resources" i.e. "RunIsar/isabelle/AutoIsar"
    * @param targetDir
    *   Destination directory
    * @throws IOException
    *   If resource not found or copy fails
    */
  def copyResources(sourceDir: String, targetDir: File): Unit = {
    require(
      sourceDir != null && targetDir != null,
      "Parameters cannot be null"
    )
    var srcDir = sourceDir
    // Append / to the input path if it doesn't end with one
    if (!srcDir.endsWith("/")) {
      srcDir += "/"
    }

    Files.createDirectories(targetDir.toPath)
    Using.resource(new ClassGraph().acceptPackages("RunIsar").scan()) { scan =>
      scan.getAllResources.asScala
        .filter(resource =>
          resource.getPathRelativeToClasspathElement().startsWith(srcDir)
        )
        .foreach { resource =>
          val targetRelPath =
            resource.getPathRelativeToClasspathElement().stripPrefix(srcDir)
          val targetPath = Paths.get(targetDir.getPath(), targetRelPath)
          val parent = targetPath.getParent
          if (parent != null) {
            Files.createDirectories(parent)
          }
          Using.resource(resource.open()) { in =>
            Files.copy(in, targetPath, StandardCopyOption.REPLACE_EXISTING)
          }
        }
    }

  }

  /**
   * Creates a temporary directory that auto-deletes on JVM exit
   * @param prefix Directory name prefix
   * @return Created directory
   */
  def createTempDir(prefix: String): File = {
    val dir = Files.createTempDirectory(prefix).toFile
    tempDirs += dir
    dir.deleteOnExit()
    
    Files.walk(dir.toPath)
      .forEach(_.toFile.deleteOnExit())

    // println(s"Created temp directory: ${dir.getAbsolutePath}")
    dir
  }
}

object TempFileManager {
  private val instance = new TempFileManager()

  def createTempDir(prefix: String): File = {
    instance.createTempDir(prefix)
  }

  def copyResources(resourcePath: String, targetDir: File): Unit = {
    instance.copyResources(resourcePath, targetDir)
  }

  def cleanupAll(): Unit = {
    instance.cleanupAll()
  }

  def cleanup(dir: File): Unit = {
    instance.cleanup(dir)
  }
}