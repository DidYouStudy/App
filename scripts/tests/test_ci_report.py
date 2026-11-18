# Co-authored-by: Claude Sonnet 5.5
"""Unit tests for scripts/ci_report.py: JUnit parsing and Markdown rendering."""

import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import ci_report  # noqa: E402

SUITE = """<testsuite tests="3">
  <testcase classname="a.B" name="passes"/>
  <testcase classname="a.B" name="fails"><failure message="expected 1&#10;but was 2">trace</failure></testcase>
  <testcase classname="a.B" name="skipped"><skipped/></testcase>
</testsuite>"""


class ParseJunitTest(unittest.TestCase):
    def test_counts_and_failure_details(self):
        with tempfile.TemporaryDirectory() as tmp:
            (Path(tmp) / "sub").mkdir()
            (Path(tmp) / "sub" / "TEST-a.xml").write_text(SUITE, encoding="utf-8")
            totals = ci_report.parse_junit_dir(tmp)
        self.assertEqual((totals["tests"], totals["failures"], totals["skipped"]), (3, 1, 1))
        self.assertEqual(totals["failed"], [("a.B.fails", "expected 1")])

    def test_missing_directory_gives_zeros(self):
        totals = ci_report.parse_junit_dir("/does/not/exist")
        self.assertEqual((totals["tests"], totals["failures"], totals["failed"]), (0, 0, []))

    def test_malformed_xml_is_skipped(self):
        with tempfile.TemporaryDirectory() as tmp:
            (Path(tmp) / "TEST-bad.xml").write_text("<oops", encoding="utf-8")
            self.assertEqual(ci_report.parse_junit_dir(tmp)["tests"], 0)


class RenderTest(unittest.TestCase):
    def test_duration_formats_minutes_and_seconds(self):
        self.assertEqual(
            ci_report.format_duration("2026-01-01T10:00:00Z", "2026-01-01T10:07:05Z"), "7m 05s"
        )
        self.assertEqual(ci_report.format_duration(None, None), "-")

    def test_job_table_skips_report_job(self):
        payload = {
            "jobs": [
                {"name": "Build", "conclusion": "success",
                 "started_at": "2026-01-01T10:00:00Z", "completed_at": "2026-01-01T10:01:00Z"},
                {"name": "CI report", "conclusion": None, "status": "in_progress"},
            ]
        }
        table = ci_report.render_jobs(payload)
        self.assertIn("| Build | success | 1m 00s |", table)
        self.assertNotIn("CI report", table)

    def test_failures_are_capped(self):
        totals = {"tests": 20, "failures": 12, "skipped": 0,
                  "failed": [(f"t{i}", "boom") for i in range(12)]}
        text = ci_report.render_tests("Unit tests", totals)
        self.assertIn("8 passed, 12 failed", text)
        self.assertIn("... and 2 more", text)
        self.assertNotIn("`t10`", text)

    def test_full_report_has_marker_and_run_link(self):
        empty = {"tests": 0, "failures": 0, "skipped": 0, "failed": []}
        text = ci_report.render({"jobs": []}, empty, empty, "https://example/run/1", "abcdef123456")
        self.assertTrue(text.startswith(ci_report.MARKER))
        self.assertIn("`abcdef1`", text)
        self.assertIn("https://example/run/1", text)


if __name__ == "__main__":
    unittest.main()
