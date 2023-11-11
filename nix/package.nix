pkgs:

pkgs.mkCljBin {
  projectSrc = ../.;
  name = "robot-disco/robonona";
  main-ns = "robot-disco.robonona.main";

  doCheck = true;
  checkPhase = "${pkgs.clojure}/bin/clj -M:env/test";
}
