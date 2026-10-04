# Co-authored-by: Codex AI Agent
"""Conservative CI scope detection and required-check aggregation."""

import json
import sys
from pathlib import PurePosixPath


def requires_android_ci(paths):
    """Only top-level or docs/ Markdown can bypass Android checks."""
    for path in paths:
        parsed = PurePosixPath(path)
        if not (
            parsed.suffix == ".md"
            and (len(parsed.parts) == 1 or parsed.parts[0] == "docs")
        ):
            return True
    return False


def ci_passes(needs):
    if needs["changes"]["result"] != "success":
        return False
    scope = needs["changes"]["outputs"].get("android")
    if scope not in ("true", "false"):
        return False
    expected = "success" if scope == "true" else "skipped"
    return all(
        needs[job]["result"] == expected
        for job in ("build", "android-tests", "analysis")
    )


def main(argv, stdin, stdout):
    if argv == ["changes"]:
        paths = [
            p
            for p in stdin.buffer.read()
            .decode("utf-8", errors="surrogateescape")
            .split("\0")
            if p
        ]
        print(f"android={str(requires_android_ci(paths)).lower()}", file=stdout)
        return 0
    elif argv == ["status"]:
        passed = ci_passes(json.load(stdin))
        print(
            "CI passed."
            if passed
            else "A required CI job failed, was cancelled, or unexpectedly skipped.",
            file=stdout,
        )
        return 0 if passed else 1
    else:
        print("Usage: ci_scope.py changes|status", file=stdout)
        return 1


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:], sys.stdin, sys.stdout))
