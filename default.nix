let
  pkgs = import (builtins.fetchGit {
      name = "pinned-pkgs";
      url = "https://github.com/nixos/nixpkgs-channels/";
      ref = "refs/heads/nixpkgs-unstable";
      rev = "92a047a6c4d46a222e9c323ea85882d0a7a13af8";
  }) {};
  stdenv = pkgs.stdenv;
  sbt = pkgs.callPackage sbt.nix { };
  jre = pkgs.jre;
  fetchurl = pkgs.fetchurl;
in rec {
  scastie = stdenv.mkDerivation rec {
    SBT_OPTS = "-Xms512m -Xmx1024M";
    name = "sbt-env";
    shellHook = ''
    alias cls=clear
    '';
    buildInputs = [
      pkgs.openjdk
      sbt
      pkgs.nodejs
      pkgs.yarn
      pkgs.bash
    ];
  };

  sbt = stdenv.mkDerivation rec {
    name = "sbt-${version}";
    version = "1.5.0";
 
    src = fetchurl {
      url = "https://github.com/sbt/sbt/releases/download/v1.3.13/sbt-1.3.13.tgz";
      sha256 = "854154de27a7d8c13b5a0f9a297cd1f254cc13b44588dae507e5d4fb2741bd22";
      name = "sbt.tgz";
    };

    patchPhase = ''
      echo -java-home ${jre.home} >>conf/sbtopts
    '';

    installPhase = ''
      mkdir -p $out/share/sbt $out/bin
      cp -ra . $out/share/sbt
      ln -s $out/share/sbt/bin/sbt $out/bin/
    '';

    meta = with stdenv.lib; {
      homepage = http://www.scala-sbt.org/;
      license = licenses.bsd3;
      description = "A build tool for Scala, Java and more";
      maintainers = with maintainers; [ rickynils ];
      platforms = platforms.unix;
    };
  };

}