package ps2

import chisel3._
import chisel3.util._
import _root_.circt.stage.ChiselStage

class PS2 extends Module {
  val io = IO(new Bundle {
    // tri-state
    val ps2_clock_i = Input(Bool())
    val ps2_clock_o = Output(Bool())
    val ps2_clock_t = Output(Bool())

    val ps2_data_i = Input(Bool())
    val ps2_data_o = Output(Bool())
    val ps2_data_t = Output(Bool())

    // send command to ps2
    val command = Flipped(Decoupled(UInt(8.W)))

    // receive data from ps2
    val data = Valid(UInt(8.W))
  })

  io.ps2_clock_o := false.B
  io.ps2_clock_t := true.B

  io.ps2_data_o := false.B
  io.ps2_data_t := true.B

  io.command.ready := false.B
  io.data.bits := 0.U
  io.data.valid := false.B
}

object PS2 extends App {
  ChiselStage.emitSystemVerilogFile(
    new PS2()
  )
}
