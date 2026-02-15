pkgs:

pkgs.mkCljBin {
  projectSrc = ../.;
  name = "robot-disco/robonona";
  version = pkgs.lib.trim (builtins.readFile ../VERSION);
  main-ns = "robot-disco.robonona.main";

  doCheck = true;
  checkPhase = "${pkgs.clojure}/bin/clj -M:env/test";
}
