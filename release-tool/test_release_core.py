import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from release_core import BuildResult, GitHub, GitHubError, repository_name, sha256, source_digest


class FakeGitHub(GitHub):
    def __init__(self):
        super().__init__("test-token-not-real")
        self.calls = []
        self.upload_fails = False
        self.existing_tag = False
        self.existing_draft = False

    def request(self, method, url, data=None, binary=False):
        self.calls.append((method, url, data))
        if "/git/ref/tags/" in url:
            if self.existing_tag:
                return {"object": {"sha": "abc"}}
            raise GitHubError(404, "Not Found")
        if "/releases?" in url:
            return [{"tag_name": "26.3-fabric-v1.1.0"}] if self.existing_draft else []
        if "uploads.github.com" in url:
            if self.upload_fails:
                raise GitHubError(500, "Upload failed")
            return {"state": "uploaded", "size": len(data)}
        if method == "POST":
            return {"id": 123, "html_url": "https://github.com/owner/repo/releases/123",
                    "upload_url": "https://uploads.github.com/repos/owner/repo/releases/123/assets{?name,label}"}
        if method == "PATCH":
            return {"html_url": "https://github.com/owner/repo/releases/123"}
        return {"permissions": {"push": True}, "sha": "abc"}


class ReleaseTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.project = Path(self.temp.name)
        (self.project / "src").mkdir()
        (self.project / "src/main.java").write_text("original")
        (self.project / "gradle.properties").write_text("mod_version=1.1.0\nminecraft_version=26.3\n")
        self.jar = self.project / "mod.jar"
        self.jar.write_bytes(b"jar-data-for-api-test")
        self.result = BuildResult(self.project, self.jar, sha256(self.jar), source_digest(self.project), "1.1.0", "26.3")
        self.client = FakeGitHub()
        def fake_git(project, *args):
            return {"status": "", "remote": "https://github.com/owner/repo.git", "rev-parse": "abc"}[args[0]]
        self.mock_git = patch("release_core.git", side_effect=fake_git).start()
        self.addCleanup(patch.stopall)

    def release(self, draft=True):
        return self.client.release(self.result, "owner/repo", "26.3-fabric-v1.1.0", "Release",
                                   "Notes", draft, False, lambda _: None)

    def test_draft_uploads_without_publishing(self):
        self.release()
        create = next(c for c in self.client.calls if c[0] == "POST" and "api.github" in c[1])
        self.assertTrue(create[2]["draft"])
        self.assertEqual(create[2]["target_commitish"], "abc")
        self.assertFalse(any(c[0] == "PATCH" for c in self.client.calls))

    def test_public_release_publishes_after_upload(self):
        self.release(draft=False)
        self.assertIn("uploads.github.com", self.client.calls[-2][1])
        self.assertEqual(self.client.calls[-1][0], "PATCH")
        self.assertFalse(self.client.calls[-1][2]["draft"])

    def test_failed_upload_leaves_recoverable_draft(self):
        self.client.upload_fails = True
        with self.assertRaisesRegex(RuntimeError, "https://github.com/owner/repo/releases/123"):
            self.release(draft=False)
        self.assertFalse(any(c[0] == "PATCH" for c in self.client.calls))

    def test_existing_tag_never_overwritten(self):
        self.client.existing_tag = True
        with self.assertRaisesRegex(ValueError, "Tag đã tồn tại"):
            self.release()
        self.assertFalse(any(c[0] == "POST" for c in self.client.calls))

    def test_existing_draft_never_duplicated(self):
        self.client.existing_draft = True
        with self.assertRaisesRegex(ValueError, "bản nháp"):
            self.release()
        self.assertFalse(any(c[0] == "POST" for c in self.client.calls))

    def test_changed_source_blocks_release(self):
        (self.project / "src/main.java").write_text("changed")
        with self.assertRaisesRegex(ValueError, "Source"):
            self.release()
        self.assertEqual(self.client.calls, [])

    def test_changed_jar_blocks_release(self):
        self.jar.write_bytes(b"changed")
        with self.assertRaisesRegex(ValueError, "JAR"):
            self.release()
        self.assertEqual(self.client.calls, [])

    def test_dirty_worktree_blocks_release(self):
        self.mock_git.side_effect = lambda *_: " M build.gradle"
        with self.assertRaisesRegex(ValueError, "Commit"):
            self.release()
        self.assertEqual(self.client.calls, [])

    def test_wrong_remote_blocks_release(self):
        self.mock_git.side_effect = lambda project, *args: "" if args[0] == "status" else "https://github.com/other/repo"
        with self.assertRaisesRegex(ValueError, "remote origin"):
            self.release()
        self.assertEqual(self.client.calls, [])

    def test_untrusted_upload_host_rejected_before_sending_token(self):
        with self.assertRaises(ValueError):
            GitHub("secret").request("POST", "https://attacker.example/upload", b"data", binary=True)

    def test_repository_validation(self):
        self.assertEqual(repository_name("git@github.com:owner/repo.git"), "owner/repo")
        self.assertEqual(repository_name("https://github.com/owner/repo"), "owner/repo")
        for invalid in ("owner/repo/extra", "https://example.com/owner/repo", "--repo"):
            with self.assertRaises(ValueError):
                repository_name(invalid)


if __name__ == "__main__":
    unittest.main()
