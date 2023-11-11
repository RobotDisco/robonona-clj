packages: { config, lib, pkgs, ... }:

let
  inherit (pkgs) system;
  inherit (lib) mdDoc;
  cfg = config.services.robonona;
  package = packages."${system}".default;
in
{
  options.robonona = {
    enable = lib.mkEnableOption "robonona";

    interval = lib.mkOption {
      default = "Mon *-*-* 08:30";
      type = lib.types.str;
      description = mdDoc ''
        Interval in which robonona is run and new pairings regenerated.
        For possible formats, please refer to {manpage} `systemd.time(7)`.
      '';
    };

    user = lib.mkOption {
      default = "root";
      type = lib.types.str;
      description = mdDoc ''
        Run robonona as this user, which holds the secrets.edn in their home
        directory.
      '';
    };
  };

  config = lib.mkIf (cfg.enable) {
    systemd.timers."robonona" = {
      enable = true;
      wantedBy = [ "timers.target" ];
      timerConfig = {
        OnCalendar = cfg.interval;
        Unit = "robonona.service";
      };
    };

    systemd.services.robonona = {
      enable = true;
      wants = [ "network-online.target" ];
      script = ''
        set -eu
        ${package}/bin/robonona prod
      '';
      serviceConfig = {
        Type = "oneshot";
        User = cfg.user;
      };
    };
  };
}
