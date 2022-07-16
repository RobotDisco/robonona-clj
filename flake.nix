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
          overlays = [
            clj-nix.overlays.default
            devshell.overlay
          ];
        };
      in

      {
        devShells.default = pkgs.devshell.mkShell {
          packages = [
            pkgs.clojure
          ];
          commands = [
            {
              name = "update-deps";
              help = "Update deps-lock.json";
              command = ''
                nix run github:jlesquembre/clj-nix#deps-lock
              '';
            }
          ];
        };
      });

}
