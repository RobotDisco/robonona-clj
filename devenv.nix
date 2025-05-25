{ pkgs, ... }:

{
  # https://devenv.sh/basics/
  env.GREET = "devenv";

  # https://devenv.sh/git-hooks/
  git-hooks.hooks = {
    # Clojure formatting
    cljfmt.enable = true;
    # Nix formatting
    deadnix.enable = true;
    nixfmt-rfc-style.enable = true;
  };

  # https://devenv.sh/packages/
  packages = [
    pkgs.git
    pkgs.babashka
    pkgs.clojure # for testing
    pkgs.clj-kondo # for linting
  ];

  # https://devenv.sh/languages/
  languages.clojure.enable = true;

  # https://devenv.sh/processes/
  # processes.cargo-watch.exec = "cargo-watch";

  # https://devenv.sh/services/
  # services.postgres.enable = true;

  # https://devenv.sh/scripts/
  scripts = {
    deps-tree.exec = "bb deps-tree";
    format.exec = "bb format";
    lint.exec = "bb lint";
    tests.exec = "bb test";
  };

  enterShell = ''
    echo "Available development commands:"
    echo "  deps-tree - Show dependencies"
    echo "  format    - Autoformat code"
    echo "  lint      - Static Analysis"
    echo "  tests     - Unit Tests"
    echo ""

    # Verify required tools
    ${pkgs.lib.getExe pkgs.git} --version

    ${pkgs.lib.getExe pkgs.babashka} --version
    ${pkgs.clojure}/bin/clj --version

    ${pkgs.lib.getExe pkgs.cljfmt} --version
    ${pkgs.lib.getExe pkgs.clj-kondo} --version
  '';

  # https://devenv.sh/tasks/
  # tasks = {
  #   "myproj:setup".exec = "mytool build";
  #   "devenv:enterShell".after = [ "myproj:setup" ];
  # };

  # https://devenv.sh/tests/
  enterTest = ''
    echo "Running tests"
    git --version | grep --color=auto "${pkgs.git.version}"
  '';

  # https://devenv.sh/pre-commit-hooks/
  # pre-commit.hooks.shellcheck.enable = true;

  # See full reference at https://devenv.sh/reference/options/
}
