with (import <nixpkgs> {});

stdenv.mkDerivation {
  name = "scastie";

  buildInputs =
    [
      pkgs.nodejs
      pkgs.yarn
      pkgs.bash
      pkgs.emscripten
    ];
}
