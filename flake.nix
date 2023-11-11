{
  description = "A Mattermost/BambooHR bot for culture";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
    clj-nix = {
      url = "github:jlesquembre/clj-nix";
      inputs.nixpkgs.follows = "nixpkgs";
    };

  };
  outputs = { self, nixpkgs, clj-nix }:
    let
      inherit (nixpkgs) lib;

      supportedSystems = [
        "aarch64-darwin"
        "x86_64-linux"
      ];
      forAllSystems = lib.genAttrs supportedSystems;
      pkgsFor = forAllSystems (system:
        import nixpkgs {
          inherit system;
          overlays = [
            clj-nix.overlays.default
          ];
        });
    in {
      packages = forAllSystems(system: {
        default = import ./nix/package.nix pkgsFor."${system}";
      });

      nixosModules = {
        default = import ./nix/module.nix self.packages;
      };

      apps = forAllSystems (system:
        let
          pkgs = pkgsFor."${system}";
        in
          {
            default = {
              type = "app";
              program = "${self.packages.${system}.default}/bin/robonona";
            };

            deps-lock = clj-nix.apps."${system}".deps-lock;

            test = let
              drv = pkgs.writeShellScriptBin "robonona-test" ''
                ${pkgs.clojure}/bin/clj -M:env/test
              '';
            in
              {
                type = "app";
                program = "${drv}/bin/robonona-test";
              };
          });

      devShells = forAllSystems (system:
        let pkgs = pkgsFor."${system}";
        in {
          default = pkgs.mkShell {
            nativeBuildInputs = [
              pkgs.git

              pkgs.ripgrep

              pkgs.nixfmt

              pkgs.clojure
              pkgs.clojure-lsp
              pkgs.clj-kondo
            ];
          };
        });
    };
}
