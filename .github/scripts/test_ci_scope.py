# Co-authored-by: Codex AI Agent
import unittest

from ci_scope import ci_passes, requires_android_ci


class ScopeTests(unittest.TestCase):
    def test_documentation_only_and_empty_diff(self):
        for paths in ([], ["README.md"], ["AGENTS.md", "docs/guide/setup.md"]):
            with self.subTest(paths=paths):
                self.assertFalse(requires_android_ci(paths))

    def test_build_inputs_and_unknown_files_require_ci(self):
        for path in (
            "app/src/main/java/Main.kt", "app/src/main/assets/help.md",
            "app/src/androidTest/Test.kt", "app/src/main/res/icon.png",
            "gradle.properties", "gradle/libs.versions.toml", "gradlew",
            "settings-gradle.lockfile", ".github/workflows/ci.yml",
            ".github/scripts/ci_scope.py", "docs/example.kt", "unknown.file",
            "README.MD", "file with spaces.kt", "file\nwith newline.kt",
        ):
            with self.subTest(path=path):
                self.assertTrue(requires_android_ci(["README.md", path]))

    def test_rename_code_to_documentation_still_requires_ci(self):
        # git diff --no-renames lists both the deleted source and added destination.
        self.assertTrue(requires_android_ci(["app/Main.kt", "docs/Main.md"]))


class StatusTests(unittest.TestCase):
    def needs(self, scope="true", result="success"):
        return {
            "changes": {"result": "success", "outputs": {"android": scope}},
            **{job: {"result": result} for job in ("build", "android-tests", "analysis")},
        }

    def test_all_checks_pass(self):
        self.assertTrue(ci_passes(self.needs()))

    def test_documentation_checks_are_skipped(self):
        self.assertTrue(ci_passes(self.needs("false", "skipped")))

    def test_failed_cancelled_and_unexpectedly_skipped_jobs_fail(self):
        for job in ("changes", "build", "android-tests", "analysis"):
            for result in ("failure", "cancelled", "skipped"):
                with self.subTest(job=job, result=result):
                    needs = self.needs()
                    needs[job]["result"] = result
                    self.assertFalse(ci_passes(needs))

    def test_missing_or_invalid_scope_fails(self):
        for scope in (None, "", "invalid"):
            self.assertFalse(ci_passes(self.needs(scope)))


if __name__ == "__main__":
    unittest.main()
