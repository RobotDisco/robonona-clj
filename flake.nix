{
  description = "A Mattermost/BambooHR bot for culture";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
    flake-utils.url = "github:numtide/flake-utils";
    devshell = {
      url = "github:numtide/devshell";
      inputs.nixpkgs.follows = "nixpkgs";
    };
    clj-nix = {
      url = "github:jlesquembre/clj-nix";
      inputs.nixpkgs.follows = "nixpkgs";
    };

  };
  outputs = { self, nixpkgs, flake-utils, devshell, clj-nix }:

    flake-utils.lib.eachDefaultSystem (system:
      let
        pkgs = import nixpkgs {
          inherit system;
          overlays = [ clj-nix.overlays.default devshell.overlay ];
        };

        cljpkgs = clj-nix.packages."${system}";

      in {

        packages = rec {
          robonona = cljpkgs.mkCljBin {
            projectSrc = ./.;
            name = "robot-disco/robonona";
            main-ns = "robot-disco.robonona.main";

            doCheck = true;
            checkPhase = "clj -M:env/test";
          };
          default = robonona;
        };

        devShells.default = pkgs.devshell.mkShell {
          packages = [
            pkgs.git
            pkgs.ripgrep

            pkgs.nixfmt

            pkgs.clojure
            pkgs.clojure-lsp
            pkgs.clj-kondo
          ];
          commands = [
            {
              name = "update-deps";
              help = "Update deps-lock.json";
              command = ''
                nix run github:jlesquembre/clj-nix#deps-lock
              '';
            }
            {
              name = "run-tests";
              help = "Test project";
              command = ''
                clj -M:env/test
              '';
            }
          ];
        };
      });
}
