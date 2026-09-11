#!/usr/bin/env bash
# verify-shipped-cve.sh - fail only on CVEs in code that actually ships.
#
# The CycloneDX BOM covers every resolvable artifact, including Gradle-plugin
# and buildscript internals (Netty, Bouncy Castle, HttpClient) that never enter
# the APK. Gating on the raw BOM would cry wolf on every AGP bump. Instead this
# intersects osv-scanner's findings with the DEX content: a vulnerable artifact
# fails the build only when its classes are present in the shipped APK.
#
#   Usage: bash scripts/verify-shipped-cve.sh <apk> <osv-scanner-json>
#   Deps:  unzip, python3. osv-scanner itself runs in CI before this.
set -euo pipefail
APK=${1:?apk path required}
OSV_JSON=${2:?osv-scanner json required}

DEX_STRINGS=$(mktemp)
trap 'rm -f "$DEX_STRINGS"' EXIT
unzip -p "$APK" 'classes*.dex' | strings > "$DEX_STRINGS"

VIOLATIONS=$(python3 - "$OSV_JSON" "$DEX_STRINGS" <<'EOF'
import json, sys, re
osv = json.load(open(sys.argv[1]))
dex = open(sys.argv[2], errors="ignore").read()
hits = set()
for res in osv.get("results", []):
    for p in res.get("packages", []):
        pkg = p.get("package", {})
        name = pkg.get("name", "")          # group:artifact
        vulns = p.get("vulnerabilities", [])
        if not vulns or ":" not in name:
            continue
        group, artifact = name.split(":", 1)
        # group a.b.c + artifact x-y -> DEX prefixes La/b/c/ or Lx/y/
        prefixes = [group.replace(".", "/") + "/",
                    artifact.replace("-", "/").replace(".", "/") + "/"]
        if any(re.search(r"L" + re.escape(px), dex) for px in prefixes):
            hits.add("{}@{} ({})".format(
                name, pkg.get("version"),
                ", ".join(v.get("id") for v in vulns)))
for h in sorted(hits):
    print(h)
EOF
)

if [ -n "$VIOLATIONS" ]; then
  echo "::error::CVEs present in shipped DEX code:"
  echo "$VIOLATIONS"
  exit 1
fi
echo "OK: no known CVEs in shipped DEX classes."
