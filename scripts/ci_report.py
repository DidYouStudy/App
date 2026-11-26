# Co-authored-by: Claude Sonnet 5.5
"""Render a Markdown summary of a CI run for the pull request comment.

Reads the GitHub "list jobs for a workflow run" JSON and JUnit XML result folders,
then prints Markdown on stdout. Standard library only, so it runs on a bare runner.
"""

import argparse
import json
from datetime import datetime
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

# Hidden marker used to find and update the same PR comment on every run.
MARKER = "<!-- ci-report -->"
# Cap on listed failures so a mass failure does not produce a huge comment.
MAX_FAILURES = 10
# Jobs that are not worth a row (the report itself and the publish step).
IGNORED_JOBS = {"CI report"}
# Shown when the run was cancelled, so partial results are not mistaken for a final verdict.
CANCELLED_BANNER = (
    "> [!WARNING]\n"
    "> This run was cancelled (probably replaced by a newer push), so the results below are partial."
)


def parse_junit_dir(directory):
    """Sum the JUnit XML files under `directory`.

    Returns a dict with `tests`, `failures`, `skipped` counts and a `failed` list of
    (test name, first line of the message). Missing folders give all zeros.
    """
    totals = {"tests": 0, "failures": 0, "skipped": 0, "failed": []}
    root = Path(directory)
    if not root.is_dir():
        return totals
    for xml_file in sorted(root.rglob("TEST-*.xml")):
        try:
            suite = ET.parse(xml_file).getroot()
        except ET.ParseError:
            continue
        for case in suite.iter("testcase"):
            totals["tests"] += 1
            if case.find("skipped") is not None:
                totals["skipped"] += 1
            problem = case.find("failure")
            if problem is None:
                problem = case.find("error")
            if problem is not None:
                totals["failures"] += 1
                message = (problem.get("message") or problem.text or "").strip()
                name = f"{case.get('classname', '')}.{case.get('name', '')}".strip(".")
                totals["failed"].append((name, message.splitlines()[0] if message else ""))
    return totals


def format_duration(started, completed):
    """Format the gap between two ISO timestamps as `Xm YYs`, or `-` if unknown."""
    if not started or not completed:
        return "-"
    fmt = "%Y-%m-%dT%H:%M:%SZ"
    seconds = int((datetime.strptime(completed, fmt) - datetime.strptime(started, fmt)).total_seconds())
    return f"{seconds // 60}m {seconds % 60:02d}s"


def render_jobs(jobs_json):
    """Render the job table from the GitHub jobs API payload.

    Rows follow the order the jobs started, with a skipped job (whose timestamps are
    meaningless) shown without a duration.
    """
    rows = ["| Job | Result | Duration |", "|---|---|---|"]
    jobs = sorted(jobs_json.get("jobs", []), key=lambda job: job.get("started_at") or "")
    for job in jobs:
        if job.get("name") in IGNORED_JOBS:
            continue
        result = job.get("conclusion") or job.get("status") or "unknown"
        if result == "skipped":
            duration = "-"
        else:
            duration = format_duration(job.get("started_at"), job.get("completed_at"))
        rows.append(f"| {job.get('name')} | {result} | {duration} |")
    return "\n".join(rows)


def render_tests(title, totals):
    """Render one test section: counts plus the failed tests, capped at MAX_FAILURES."""
    if totals["tests"] == 0:
        # "0 failed" would read like a pass, so say plainly that nothing ran.
        return f"**{title}:** no results (the job may have stopped or been cancelled before running tests)"
    passed = totals["tests"] - totals["failures"] - totals["skipped"]
    lines = [
        f"**{title}:** {passed} passed, {totals['failures']} failed, {totals['skipped']} skipped"
        f" ({totals['tests']} total)"
    ]
    for name, message in totals["failed"][:MAX_FAILURES]:
        lines.append(f"- `{name}`: {message}")
    hidden = len(totals["failed"]) - MAX_FAILURES
    if hidden > 0:
        lines.append(f"- ... and {hidden} more")
    return "\n".join(lines)


def is_cancelled(jobs_json):
    """True when any job of the run ended as cancelled (for example a newer push replaced it)."""
    return any(job.get("conclusion") == "cancelled" for job in jobs_json.get("jobs", []))


def render(jobs_json, unit, connected, run_url, sha):
    """Assemble the whole Markdown comment, with a banner when the run was cancelled."""
    parts = [MARKER, f"## CI report for `{sha[:7]}`"]
    if is_cancelled(jobs_json):
        parts.append(CANCELLED_BANNER)
    parts += [
        render_jobs(jobs_json),
        render_tests("Unit tests", unit),
        render_tests("Emulator tests", connected),
        f"[Full run]({run_url})",
    ]
    return "\n\n".join(parts)


def main(argv=None):
    """Command line entry point; prints the report to stdout."""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--jobs", required=True, help="JSON file from the run jobs API")
    parser.add_argument("--unit-dir", required=True)
    parser.add_argument("--connected-dir", required=True)
    parser.add_argument("--run-url", required=True)
    parser.add_argument("--sha", required=True)
    args = parser.parse_args(argv)
    jobs_json = json.loads(Path(args.jobs).read_text(encoding="utf-8"))
    sys.stdout.write(
        render(
            jobs_json,
            parse_junit_dir(args.unit_dir),
            parse_junit_dir(args.connected_dir),
            args.run_url,
            args.sha,
        )
        + "\n"
    )


if __name__ == "__main__":
    main()
