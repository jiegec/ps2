package ps2

import svsim._
import scala.reflect._
import chisel3.RawModule
import chisel3.simulator._
import java.nio.file.Files
import java.io.File
import scala.reflect.io.Directory
import org.scalatest.freespec.AnyFreeSpec

// custom EphemeralSimulator to add options to verilator
object Simulator extends PeekPokeAPI {

  def simulate[T <: RawModule: ClassTag](
      module: => T
  )(body: (T) => Unit): Unit = {
    // setup workspace based on module name
    val className = implicitly[ClassTag[T]].runtimeClass.getSimpleName
    new DefaultSimulator(
      s"test_run_dir/${className}"
    ).simulate(module)({ module =>
      // enable tracing
      module.controller.setTraceEnabled(true)
      body(module.wrapped)
    }).result
  }

  // use verilator
  private class DefaultSimulator(val workspacePath: String)
      extends SingleBackendSimulator[verilator.Backend] {
    val backend = verilator.Backend.initializeFromProcessEnvironment()
    val tag = "default"
    val commonCompilationSettings = CommonCompilationSettings()
    val backendSpecificCompilationSettings =
      verilator.Backend.CompilationSettings(
        traceStyle =
          Some(verilator.Backend.CompilationSettings.TraceStyle.Vcd())
      )
  }
}
