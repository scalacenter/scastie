with (import <nixpkgs> {});

stdenv.mkDerivation {
  name = "scastie";

  buildInputs =
    let
      sbtWithJRE = pkgs.sbt.override {
        jre = pkgs.jdk17_headless;
      };
    in
      [
        pkgs.nodejs
        pkgs.yarn
        pkgs.bash
        sbtWithJRE
      ];
}
