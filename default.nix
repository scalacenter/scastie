with import (builtins.fetchGit {
  name = "nixos-unstable-2021-11-15";
  url = "https://github.com/nixos/nixpkgs/";
  ref = "refs/heads/nixos-unstable";
  rev = "931ab058daa7e4cd539533963f95e2bb0dbd41e6";
}) {};
  
stdenv.mkDerivation {
  name = "scastie";

  buildInputs = with pkgs; [ sbt nodejs yarn bash ];

  SBT_OPTS = "";

  shellHook = ''
    alias cls=clear
  '';
 }
