package ps2

import chisel3._
import chisel3.util._
import _root_.circt.stage.ChiselStage

class TriState[T <: Data](gen: T) extends Bundle {
  val i = Input(Bool())
  val o = Output(Bool())
  val t = Output(Bool())

  // wired and
  def connectTo(other: TriState[T]) = {
    val data = Wire(Bool())

    when(t && other.t) {
      // pull-up
      data := true.B
    }.elsewhen(t && !other.t) {
      data := other.o
    }.elsewhen(!t && other.t) {
      data := o
    }.otherwise {
      data := other.o & o
    }

    i := data
    other.i := data
  }
}

class PS2Interface extends Bundle {
  val clock = new TriState(Bool())
  val data = new TriState(Bool())
}

object PS2State extends ChiselEnum {
  val sIdle, sSendClockLow, sSendDataLow, sSendReqAndParity, sSendStop,
      sSendAck, sSendErr, sRecvResp, sRecvStop, sRecvDone = Value
}

// default to 50MHz
// reference: https://www.burtonsys.com/ps2_chapweske.htm
class PS2Controller(clockFreqInMHz: Int = 50, queueSize: Int = 16)
    extends Module {
  val io = IO(new Bundle {
    // tri-state
    val ps2 = new PS2Interface()

    // send req to ps2
    val req = Flipped(Decoupled(UInt(8.W)))

    // the device does not respond in time
    val req_error = Output(Bool())

    // receive resp from ps2
    val resp = Decoupled(UInt(8.W))

    // parity validation failed
    val resp_error = Output(Bool())
  })

  // add buffer for received resp
  val respQueue = Module(new Queue(UInt(8.W), queueSize))
  io.resp <> respQueue.io.deq

  val state = RegInit(PS2State.sIdle)

  // default wiring
  respQueue.io.enq.noenq()
  io.ps2.clock.o := false.B
  io.ps2.clock.t := true.B
  io.ps2.data.o := false.B
  io.ps2.data.t := true.B
  io.req.ready := false.B
  io.req_error := false.B
  io.resp_error := false.B

  val counter100us = new Counter(100 * clockFreqInMHz)
  val counter15ms = new Counter(15000 * clockFreqInMHz)
  val counter2ms = new Counter(2000 * clockFreqInMHz)

  // 8 bits + parity
  val curReq = Reg(UInt(9.W))
  val counterSend = new Counter(9)

  val curResp = Reg(UInt(9.W))
  val counterRecv = new Counter(9)

  // handle glitches
  val lastClockSamples = RegInit(7.U(3.W))
  lastClockSamples := Cat(lastClockSamples(1, 0), io.ps2.clock.i)
  val clockHigh = WireInit(lastClockSamples.asBools.reduce(_ & _))
  val clockLow = WireInit((~lastClockSamples).asBools.reduce(_ & _))

  val lastClock = RegInit(true.B)
  when(clockHigh) {
    lastClock := true.B
  }.elsewhen(clockLow) {
    lastClock := false.B
  }
  val clockRise = WireInit(clockHigh && ~lastClock)
  val clockFall = WireInit(clockLow && lastClock)

  switch(state) {
    is(PS2State.sIdle) {
      // check for data input
      when(clockRise && !io.ps2.data.i) {
        // rising edge & start
        counterRecv.reset()
        state := PS2State.sRecvResp
      }.elsewhen(io.ps2.clock.i) {
        // bus is free
        io.req.ready := true.B
        when(io.req.valid) {
          // send
          // parity
          curReq := Cat(io.req.bits.asBools.reduce(_ ^ _) ^ true.B, io.req.bits)
          state := PS2State.sSendClockLow
          counter100us.reset()
        }
      }
    }
    is(PS2State.sSendClockLow) {
      // bring the clock line low for at least 100 microseconds
      io.ps2.clock.t := false.B
      io.ps2.clock.o := false.B
      when(counter100us.inc()) {
        // wrap
        state := PS2State.sSendDataLow
        counter15ms.reset()
      }
    }
    is(PS2State.sSendDataLow) {
      // bring the data line low
      // release the clock line
      io.ps2.data.t := false.B
      io.ps2.data.o := false.B

      // wait for the device to bring the clock line low
      // detect negedge
      when(clockFall) {
        state := PS2State.sSendReqAndParity
        counterSend.reset()
        counter2ms.reset()
      }.elsewhen(counter15ms.inc()) {
        // 15ms timeout
        state := PS2State.sSendErr
      }
    }
    is(PS2State.sSendReqAndParity) {
      io.ps2.data.t := false.B
      io.ps2.data.o := curReq(counterSend.value)

      // wait for the device to bring the clock line low
      // detect negedge
      when(clockFall) {
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
      when(clockFall) {
        state := PS2State.sSendAck
      }
    }
    is(PS2State.sSendAck) {
      // wait for the device to release clock
      when(clockRise) {
        // finish transmission
        state := PS2State.sIdle
      }
    }
    is(PS2State.sSendErr) {
      io.req_error := true.B
      state := PS2State.sIdle
    }
    is(PS2State.sRecvResp) {
      // although the web page says to sample on falling edge
      // we sample on rising edge for simplicity
      when(clockRise) {
        curResp := curResp.bitSet(counterRecv.value, io.ps2.data.i)
        when(counterRecv.inc()) {
          state := PS2State.sRecvStop
        }
      }
    }
    is(PS2State.sRecvStop) {
      // wait for last rising edge
      when(clockRise) {
        state := PS2State.sRecvDone
      }
    }
    is(PS2State.sRecvDone) {
      // save resp to queue if parity was correct
      // drop data if queue was full
      val valid = curResp.asBools.reduce(_ ^ _)
      respQueue.io.enq.valid := valid
      respQueue.io.enq.bits := curResp(7, 0)
      io.resp_error := !valid
      state := PS2State.sIdle
    }
  }
}

object PS2 extends App {
  ChiselStage.emitSystemVerilogFile(
    new PS2Controller(),
    Array(),
    Array("--lowering-options=disallowLocalVariables")
  )
}
