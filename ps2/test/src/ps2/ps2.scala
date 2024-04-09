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

    // the device does not respond in time
    val req_error = Output(Bool())

    // receive resp from ps2
    val resp = Decoupled(UInt(8.W))

    // parity validation failed
    val resp_error = Output(Bool())
  })

  val dut = Module(new PS2Controller(clockFreqInMHz))
  io.req <> dut.io.req
  io.req_error := dut.io.req_error
  io.resp <> dut.io.resp
  io.resp_error := dut.io.resp_error

  // simulate bus
  io.ps2.clock.connectTo(dut.io.ps2.clock)
  io.ps2.data.connectTo(dut.io.ps2.data)
}

class PS2Spec extends AnyFreeSpec {
  s"test ps2 request" in {
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

  s"test ps2 recv" in {
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

      // start to send
      val data = 0x12
      val parity = true.B
      dut.io.ps2.data.t.poke(false.B)
      dut.io.ps2.clock.t.poke(false.B)

      // start bit
      dut.io.ps2.data.o.poke(false.B)
      dut.clock.step(16)

      // clock pulse
      dut.io.ps2.clock.o.poke(false.B)
      dut.clock.step(16)

      dut.io.ps2.clock.o.poke(true.B)
      dut.clock.step(16)

      for (i <- 0 to 7) {
        dut.io.ps2.data.o.poke(((data & (1 << i)) != 0).B)
        dut.clock.step(16)

        // clock pulse
        dut.io.ps2.clock.o.poke(false.B)
        dut.clock.step(16)

        dut.io.ps2.clock.o.poke(true.B)
        dut.clock.step(16)
      }

      // parity
      dut.io.ps2.data.o.poke(parity)
      dut.clock.step(16)

      // clock pulse
      dut.io.ps2.clock.o.poke(false.B)
      dut.clock.step(16)

      dut.io.ps2.clock.o.poke(true.B)
      dut.clock.step(16)

      // stop
      dut.io.ps2.data.o.poke(true.B)
      dut.clock.step(16)

      // clock pulse
      dut.io.ps2.clock.o.poke(false.B)
      dut.clock.step(16)

      dut.io.ps2.clock.o.poke(true.B)
      dut.clock.step(16)

      // read data from ps2
      dut.io.resp.ready.poke(true.B)
      while (dut.io.resp.valid.peek().litToBoolean == false) {
        dut.clock.step()
      }
      assert(dut.io.resp.bits.peek().litValue == data)
      dut.clock.step()
      dut.io.resp.ready.poke(false.B)

      dut.clock.step(16)
    }
  }
}
