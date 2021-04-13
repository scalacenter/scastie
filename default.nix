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
      url = "https://github.com/sbt/sbt/releases/download/v1.5.0/sbt-1.5.0.tgz";
      sha256 = "aadf110a48ea77c02ce63a9cc033d9fcd02b634e89e8f22dfe13ed21592042b6";
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