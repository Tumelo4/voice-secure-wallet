# Mobile dependency security exceptions

The mobile security gate fails on every high or critical npm advisory except
the two explicitly identified `image-size` denial-of-service advisories in
`scripts/audit-mobile-dependencies.mjs`.

Those advisories currently declare every published `image-size` release,
including 2.0.2, vulnerable. The package is reached through Expo's Metro build
tooling and is not bundled into the exported web application. CI processes only
repository-owned image assets, so an attacker cannot supply a malicious ICNS,
JXL, or HEIF file to this parser in the deployment path. Changing from the
existing 1.2.1 to the latest 2.0.2 cannot remediate the advisories and causes
GitHub to reject it as a newly introduced vulnerable dependency. The gate pins
and verifies 1.2.1, rejects every other high/critical advisory, and fails if the
exception identifiers change. Remove the exceptions as soon as upstream ships
a patched release.
