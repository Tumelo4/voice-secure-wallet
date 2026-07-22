#!/usr/bin/env python3
"""Classify changed files into mandatory high-risk validation groups."""

from __future__ import annotations

import argparse
import fnmatch
import subprocess
from pathlib import Path


GROUPS = {
    "financial_integration": (
        "services/ledger-service/**",
        "services/payment-service/**",
        "tests/integration-tests/**",
    ),
    "security_boundary": (
        "services/identity-service/**",
        "services/voice-service/**",
        "services/api-adapter-service/**",
    ),
    "migration_validation": ("services/*/src/main/resources/db/migration/**",),
    "terraform_validation": ("infra/**",),
    "workflow_review": (
        ".github/workflows/**",
        ".github/CODEOWNERS",
        ".github/merge-policy.yml",
    ),
    "contract_compatibility": ("contracts/**", "tests/contract-tests/**"),
}


def changed_files(base: str, head: str) -> list[str]:
    result = subprocess.run(
        ["git", "diff", "--name-only", f"{base}...{head}"],
        check=True,
        capture_output=True,
        text=True,
    )
    return [line for line in result.stdout.splitlines() if line]


def classify(paths: list[str]) -> dict[str, bool]:
    return {
        group: any(fnmatch.fnmatch(path, pattern) for path in paths for pattern in patterns)
        for group, patterns in GROUPS.items()
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--base", required=True)
    parser.add_argument("--head", required=True)
    parser.add_argument("--github-output", type=Path)
    arguments = parser.parse_args()

    paths = changed_files(arguments.base, arguments.head)
    results = classify(paths)
    lines = [f"{name}={'true' if matched else 'false'}" for name, matched in results.items()]
    if arguments.github_output:
        with arguments.github_output.open("a", encoding="utf-8") as output:
            output.write("\n".join(lines) + "\n")
    print("Changed paths:")
    print("\n".join(f"- {path}" for path in paths) or "- none")
    print("Required evidence:")
    print("\n".join(f"- {name}: {str(matched).lower()}" for name, matched in results.items()))


if __name__ == "__main__":
    main()
