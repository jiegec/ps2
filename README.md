# PS/2 Controller IP

A PS/2 controller IP written in Chisel.

Interface:

- Tri-state ps2 clock and data
- Ready-valid request and response

Interface in verilog:

```verilog
module PS2Controller(
  input        clock,
               reset,
               io_ps2_clock_i,
  output       io_ps2_clock_o,
               io_ps2_clock_t,
  input        io_ps2_data_i,
  output       io_ps2_data_o,
               io_ps2_data_t,
               io_req_ready,
  input        io_req_valid,
  input  [7:0] io_req_bits,
  output       io_req_error,
  input        io_resp_ready,
  output       io_resp_valid,
  output [7:0] io_resp_bits,
  output       io_resp_error
);
```

To generate controller IP for 50MHz clock:

```shell
mill ps2.runMain ps2.PS2
```

SystemVerilog source is written at `PS2Controller.sv`. You can change and regenerate code if the clock frequency is different. But if you do not care about timeouts and run your design under a lower clock frequency, you do not need to change it.

If you do not have the environment for running mill, you can download prebuilt RTL from GitHub Actions artifact.

Reference:

- [PS/2 Mouse/Keyboard Protocol by Adam Chapweske](https://www.burtonsys.com/ps2_chapweske.htm)