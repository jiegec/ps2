package ps2

import chisel3._
import chisel3.util._
import _root_.circt.stage.ChiselStage

object PS2State extends ChiselEnum {
  val sIdle, sSendClockLow, sSendDataLow, sSendReqAndParity, sSendStop,
      sSendAck, sSendErr, sRecv = Value
}

// default to 50MHz
// reference: https://www.burtonsys.com/ps2_chapweske.htm
class PS2(clockFreqInMHz: Int = 50) extends Module {
  val io = IO(new Bundle {
    // tri-state
    val ps2_clock_i = Input(Bool())
    val ps2_clock_o = Output(Bool())
    val ps2_clock_t = Output(Bool())

    val ps2_data_i = Input(Bool())
    val ps2_data_o = Output(Bool())
    val ps2_data_t = Output(Bool())

    // send req to ps2

    val req = Flipped(Decoupled(UInt(8.W)))

    val req_error = Output(Bool())

    // receive resp from ps2
    val resp = Decoupled(UInt(8.W))
  })

  // add buffer for received resp
  val respQueue = Module(new Queue(UInt(8.W), 16))
  io.resp <> respQueue.io.deq

  val state = RegInit(PS2State.sIdle)

  val lastData = RegNext(io.ps2_data_i)
  val lastClock = RegNext(io.ps2_clock_i)

  // default wiring
  respQueue.io.enq.noenq()
  io.ps2_clock_o := false.B
  io.ps2_clock_t := true.B
  io.ps2_data_o := false.B
  io.ps2_data_t := true.B
  io.req.ready := false.B
  io.req_error := false.B

  val counter100us = new Counter(100 * clockFreqInMHz)
  val counter15ms = new Counter(15000 * clockFreqInMHz)
  val counter2ms = new Counter(2000 * clockFreqInMHz)

  // 8 bits + parity
  val curReq = Reg(UInt(9.W))
  val counterSend = new Counter(9)

  switch(state) {
    is(PS2State.sIdle) {
      io.req.ready := true.B
      when(io.req.valid) {
        // send
        // parity
        curReq := Cat(curReq.asBools.reduce(_ ^ _) ^ true.B, io.req.bits)
        state := PS2State.sSendClockLow
        counter100us.reset()
      }
    }
    is(PS2State.sSendClockLow) {
      // bring the clock line low for at least 100 microseconds
      io.ps2_clock_t := false.B
      io.ps2_clock_o := false.B
      when(counter100us.inc()) {
        // wrap
        state := PS2State.sSendDataLow
        counter15ms.reset()
      }
    }
    is(PS2State.sSendDataLow) {
      // bring the data line low
      // release the clock line
      io.ps2_data_t := false.B
      io.ps2_data_o := false.B

      // wait for the device to bring the clock line low
      // detect negedge
      when(!io.ps2_clock_i && lastClock) {
        state := PS2State.sSendReqAndParity
        counterSend.reset()
        counter2ms.reset()
      }.elsewhen(counter15ms.inc()) {
        // 15ms timeout
        state := PS2State.sSendErr
      }
    }
    is(PS2State.sSendReqAndParity) {
      io.ps2_data_t := false.B
      io.ps2_data_o := curReq(counterSend.value)

      // wait for the device to bring the clock line low
      // detect negedge
      when(!io.ps2_clock_i && lastClock) {
        when(counterSend.inc()) {
          state := PS2State.sSendStop
        }
      }.elsewhen(counter2ms.inc()) {
        // 2ms timeout
        state := PS2State.sSendErr
      }
    }
    is(PS2State.sSendStop) {
      // release the data line
      // wait for the device to bring clock low
      when(!io.ps2_clock_i && lastClock) {
        state := PS2State.sSendAck
      }
    }
    is(PS2State.sSendAck) {
      // wait for the device to release clock
      when(io.ps2_clock_i && !lastClock) {
        // finish transmission
        state := PS2State.sIdle
      }
    }
    is(PS2State.sSendErr) {
      io.req_error := true.B
      state := PS2State.sIdle
    }
  }
}

object PS2 extends App {
  ChiselStage.emitSystemVerilogFile(
    new PS2()
  )
}
