package ps2

import chisel3._
import chisel3.experimental.BundleLiterals._
import org.scalatest.freespec.AnyFreeSpec
import ps2.Simulator._
import chisel3.util.Decoupled

// simulate tri-state bus for ps2
class PS2BusSim(clockFreqInMHz: Int = 50) extends Module {
  val io = IO(new Bundle {
    // device side
    // tri-state
    val ps2 = Flipped(new PS2Interface())

    // send req to ps2
    val req = Flipped(Decoupled(UInt(8.W)))

    val req_error = Output(Bool())

    // receive resp from ps2
    val resp = Decoupled(UInt(8.W))
  })

  val dut = Module(new PS2Controller(clockFreqInMHz))
  io.req <> dut.io.req
  io.req_error := dut.io.req_error
  io.resp <> dut.io.resp

  // simulate bus
  val ps2_clock = Wire(Bool())
  when(io.ps2.clock.t && dut.io.ps2.clock.t) {
    // pull-up
    ps2_clock := true.B
  }.elsewhen(io.ps2.clock.t && !dut.io.ps2.clock.t) {
    ps2_clock := dut.io.ps2.clock.o
  }.elsewhen(!io.ps2.clock.t && dut.io.ps2.clock.t) {
    ps2_clock := io.ps2.clock.o
  }.otherwise {
    assert(false.B)
    ps2_clock := false.B
  }
  io.ps2.clock.i := ps2_clock
  dut.io.ps2.clock.i := ps2_clock

  // simulate bus
  val ps2_data = Wire(Bool())
  when(io.ps2.data.t && dut.io.ps2.data.t) {
    // pull-up
    ps2_data := true.B
  }.elsewhen(io.ps2.data.t && !dut.io.ps2.data.t) {
    ps2_data := dut.io.ps2.data.o
  }.elsewhen(!io.ps2.data.t && dut.io.ps2.data.t) {
    ps2_data := io.ps2.data.o
  }.otherwise {
    assert(false.B)
    ps2_data := false.B
  }
  io.ps2.data.i := ps2_data
  dut.io.ps2.data.i := ps2_data
}

class PS2Spec extends AnyFreeSpec {
  s"simulate ps2 request" in {
    simulate(new PS2BusSim()) { dut =>
      dut.io.ps2.clock.t.poke(true.B)
      dut.io.ps2.data.t.poke(true.B)
      dut.reset.poke(true.B)
      dut.clock.step(16)
      dut.reset.poke(false.B)
      dut.clock.step(16)

      // initially, clock and data are both high
      assert(dut.io.ps2.clock.i.peek().litToBoolean)
      assert(dut.io.ps2.data.i.peek().litToBoolean)

      // send command
      dut.io.req.valid.poke(true.B)
      val data = 0x12
      dut.io.req.bits.poke(data.U)
      while (dut.io.req.ready.peek().litToBoolean == false) {
        dut.clock.step()
      }
      dut.clock.step()
      dut.io.req.valid.poke(false.B)

      // wait for clock line low
      while (
        dut.io.ps2.clock.i
          .peek()
          .litToBoolean != false
      ) {
        dut.clock.step()
      }

      // wait for data line low
      while (
        dut.io.ps2.data.i
          .peek()
          .litToBoolean != false
      ) {
        dut.clock.step()
      }
      dut.clock.step(16)

      // bring clock low
      dut.io.ps2.clock.o.poke(false.B)
      dut.io.ps2.clock.t.poke(false.B)

      dut.clock.step(16)

      // bring the clock line high and low
      for (i <- 0 to 7) {
        // device read the data in rising edge
        dut.io.ps2.clock.o.poke(true.B)
        val bit = dut.io.ps2.data.i.peek().litToBoolean
        // validate data
        assert(bit == ((data & (1 << i)) != 0))
        dut.clock.step(16)

        dut.io.ps2.clock.o.poke(false.B)
        dut.clock.step(16)
      }

      // read parity
      dut.io.ps2.clock.o.poke(true.B)
      // device read the data in rising edge
      val bit = dut.io.ps2.data.i.peek().litToBoolean
      dut.clock.step(16)

      // finish parity
      dut.io.ps2.clock.o.poke(false.B)
      dut.clock.step(16)

      // stop
      dut.io.ps2.clock.o.poke(true.B)
      dut.clock.step(16)

      // bring data low
      dut.io.ps2.data.o.poke(false.B)
      dut.io.ps2.data.t.poke(false.B)
      dut.clock.step(16)

      // bring clock low
      dut.io.ps2.clock.o.poke(false.B)
      dut.io.ps2.clock.t.poke(false.B)
      dut.clock.step(16)

      // release data and clock
      dut.io.ps2.data.t.poke(true.B)
      dut.io.ps2.clock.t.poke(true.B)
      dut.clock.step(16)

      // verify that is is ready to accept next req
      dut.clock.step(64)
      assert(dut.io.req.ready.peek().litToBoolean)
    }
  }

  s"test ps2 15ms timeout" in {
    simulate(new PS2BusSim()) { dut =>
      dut.io.ps2.clock.t.poke(true.B)
      dut.io.ps2.data.t.poke(true.B)
      dut.reset.poke(true.B)
      dut.clock.step(16)
      dut.reset.poke(false.B)
      dut.clock.step(16)

      // initially, clock and data are both high
      assert(dut.io.ps2.clock.i.peek().litToBoolean)
      assert(dut.io.ps2.data.i.peek().litToBoolean)

      // send command
      dut.io.req.valid.poke(true.B)
      val data = 0x12
      dut.io.req.bits.poke(data.U)
      while (dut.io.req.ready.peek().litToBoolean == false) {
        dut.clock.step()
      }
      dut.clock.step()
      dut.io.req.valid.poke(false.B)

      // don't do anything more
      // wait for req_error
      while (dut.io.req_error.peek().litToBoolean == false) {
        dut.clock.step()
      }

      // verify that is is ready to accept next req
      dut.clock.step(64)
      assert(dut.io.req.ready.peek().litToBoolean)
    }
  }

  s"test ps2 2ms timeout" in {
    simulate(new PS2BusSim()) { dut =>
      dut.io.ps2.clock.t.poke(true.B)
      dut.io.ps2.data.t.poke(true.B)
      dut.reset.poke(true.B)
      dut.clock.step(16)
      dut.reset.poke(false.B)
      dut.clock.step(16)

      // initially, clock and data are both high
      assert(dut.io.ps2.clock.i.peek().litToBoolean)
      assert(dut.io.ps2.data.i.peek().litToBoolean)

      // send command
      dut.io.req.valid.poke(true.B)
      val data = 0x12
      dut.io.req.bits.poke(data.U)
      while (dut.io.req.ready.peek().litToBoolean == false) {
        dut.clock.step()
      }
      dut.clock.step()
      dut.io.req.valid.poke(false.B)

      // wait for clock line low
      while (
        dut.io.ps2.clock.i
          .peek()
          .litToBoolean != false
      ) {
        dut.clock.step()
      }

      // wait for data line low
      while (
        dut.io.ps2.data.i
          .peek()
          .litToBoolean != false
      ) {
        dut.clock.step()
      }
      dut.clock.step(16)

      // bring clock low
      dut.io.ps2.clock.o.poke(false.B)
      dut.io.ps2.clock.t.poke(false.B)

      dut.clock.step(16)

      // release clock
      dut.io.ps2.clock.t.poke(true.B)

      // don't do anything more
      // wait for req_error
      while (dut.io.req_error.peek().litToBoolean == false) {
        dut.clock.step()
      }

      // verify that is is ready to accept next req
      dut.clock.step(64)
      assert(dut.io.req.ready.peek().litToBoolean)
    }
  }
}
