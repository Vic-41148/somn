# Security Policy

## Supported versions

| Version | Supported |
| ------- | --------- |
| latest `v*` tag | yes |
| older tags | no — update to the latest release |

## Reporting a vulnerability

**Do not open a public issue.** Use GitHub's
[private vulnerability reporting](../../security/advisories/new)
(Settings → Security covers the maintainer side) so details stay private
until a fix ships.

What helps: affected version, steps to reproduce, and what data (if any) an
attacker could reach. Sleep data, NAS credentials, and backups are the crown
jewels here — anything touching those gets priority.

## What this project already does

- Zero telemetry: crashes stay in app-private storage until the user
  deliberately copies one out (Settings → About → crash log).
- No Google Play Services in any shipped build (asserted in CI).
- Release APKs ship a CycloneDX SBOM and Sigstore signatures.
