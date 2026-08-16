{ pkgs ? import (fetchTarball "https://github.com/NixOS/nixpkgs/archive/8be7bd0c83f12e2e3bbba07c9044d6fed9e66f7f.tar.gz") {}
}:

pkgs.mkShell {
  buildInputs = with pkgs; [
    mill
    boost
    iverilog
    verilator
  ];
}
