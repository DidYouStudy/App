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


if __name__ == "__main__":
    if sys.argv[1] == "changes":
        paths = [
            p
            for p in sys.stdin.buffer.read()
            .decode("utf-8", errors="surrogateescape")
            .split("\0")
            if p
        ]
        print(f"android={str(requires_android_ci(paths)).lower()}")
    elif sys.argv[1] == "status":
        passed = ci_passes(json.load(sys.stdin))
        print(
            "CI passed."
            if passed
            else "A required CI job failed, was cancelled, or unexpectedly skipped."
        )
        sys.exit(0 if passed else 1)
    else:
        sys.exit("Usage: ci_scope.py changes|status")
