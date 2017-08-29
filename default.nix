let
  pkgs = import <nixpkgs> {};
  stdenv = pkgs.stdenv;
  sbt = pkgs.callPackage sbt.nix { };
  jre = pkgs.jre;
  fetchurl = pkgs.fetchurl;
in rec {
  scastie = stdenv.mkDerivation rec {

    name = "sbt-env";
    shellHook = ''
    alias cls=clear
    '';
    buildInputs = [
      pkgs.openjdk
      sbt
      pkgs.nodejs
      pkgs.yarn
    ];
  };

  sbt = stdenv.mkDerivation rec {
    name = "sbt-${version}";
    version = "0.13.16";

    src = fetchurl {
      url = "https://cocl.us/sbt01316tgz";
      sha256 = "033nvklclvbirhpsiy28d3ccmbm26zcs9vb7j8jndsc1ln09awi2";
      name = "sbt0.13.16.tgz";
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