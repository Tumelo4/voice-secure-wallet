import { readFileSync } from "node:fs";
import { spawnSync } from "node:child_process";

const acceptedAdvisories = new Set([
  "https://github.com/advisories/GHSA-5p2g-fcmc-qvqq",
  "https://github.com/advisories/GHSA-w3rx-r6r6-pgpr",
]);

const lock = JSON.parse(readFileSync("apps/mobile/package-lock.json", "utf8"));
const imageSizeVersion = lock.packages?.["node_modules/image-size"]?.version;
if (imageSizeVersion !== "2.0.2") {
  throw new Error(`Expected the reviewed image-size override at 2.0.2, found ${imageSizeVersion ?? "nothing"}`);
}

const audit = spawnSync("npm", ["audit", "--json", "--audit-level=high"], {
  cwd: "apps/mobile",
  encoding: "utf8",
  maxBuffer: 20 * 1024 * 1024,
});
if (!audit.stdout) {
  process.stderr.write(audit.stderr);
  throw new Error("npm audit did not return an advisory report");
}

const report = JSON.parse(audit.stdout);
const activeAdvisories = new Set();
for (const vulnerability of Object.values(report.vulnerabilities ?? {})) {
  for (const cause of vulnerability.via ?? []) {
    if (typeof cause === "object" && ["high", "critical"].includes(cause.severity)) {
      activeAdvisories.add(cause.url);
    }
  }
}

const unexpected = [...activeAdvisories].filter((url) => !acceptedAdvisories.has(url));
if (unexpected.length > 0) {
  throw new Error(`Unaccepted high/critical advisories:\n${unexpected.join("\n")}`);
}
if ([...activeAdvisories].some((url) => !url)) {
  throw new Error("A high/critical advisory is missing a stable identifier");
}

console.log("No unaccepted high or critical mobile dependency advisories.");
if (activeAdvisories.size > 0) {
  console.log("Accepted upstream image-size build-tool advisories:");
  for (const url of [...activeAdvisories].sort()) console.log(`- ${url}`);
}
