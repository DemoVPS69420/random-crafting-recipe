import tempfile
import unittest
from pathlib import Path

from git_sync import inspect, run, sync


class GitSyncTests(unittest.TestCase):
    def setUp(self):
        parent = Path(__file__).resolve().parent / '.test-tmp'
        parent.mkdir(exist_ok=True)
        self.temp = tempfile.TemporaryDirectory(dir=parent)
        self.root = Path(self.temp.name).resolve()
        assert self.root.is_relative_to(parent.resolve())
        self.addCleanup(self.temp.cleanup)
        self.remote = self.root / 'origin.git'
        run(self.root, 'init', '--bare', '--initial-branch=main', str(self.remote))
        self.local = self.root / 'local'
        run(self.root, 'clone', str(self.remote), str(self.local))
        self.configure(self.local)
        (self.local / 'recipe.txt').write_text('original\n')
        self.commit(self.local, 'initial')
        run(self.local, 'push', '-u', 'origin', 'main')
        self.other = self.root / 'other'
        run(self.root, 'clone', str(self.remote), str(self.other))
        self.configure(self.other)

    def configure(self, repo):
        run(repo, 'config', 'user.name', 'Git Sync Test')
        run(repo, 'config', 'user.email', 'test@example.invalid')
        run(repo, 'config', 'commit.gpgsign', 'false')

    def commit(self, repo, message):
        run(repo, 'add', '--all')
        run(repo, 'commit', '-m', message)
        return run(repo, 'rev-parse', 'HEAD').strip()

    def advance_remote(self, file='remote.txt', content='remote\n'):
        (self.other / file).write_text(content)
        commit = self.commit(self.other, 'remote change')
        run(self.other, 'push', 'origin', 'main')
        return commit

    def test_diverged_branches_keep_both_histories(self):
        (self.local / 'local.txt').write_text('local\n')
        local_head = self.commit(self.local, 'local change')
        remote_head = self.advance_remote()
        sync(inspect(self.local), '')
        for head in (local_head, remote_head):
            run(self.local, 'merge-base', '--is-ancestor', head, 'HEAD')
        self.assertEqual(run(self.local, 'rev-parse', 'HEAD'), run(self.remote, 'rev-parse', 'main'))

    def test_previewed_changes_are_committed_and_pushed(self):
        (self.local / 'recipe.txt').unlink()
        (self.local / 'new recipe.txt').write_text('new\n')
        preview = inspect(self.local)
        self.assertEqual(len(preview.paths), 2)
        sync(preview, 'recipe update')
        self.assertFalse(run(self.local, 'status', '--porcelain').strip())
        self.assertEqual(run(self.remote, 'show', 'main:new recipe.txt'), 'new\n')

    def test_conflict_aborts_merge_preserving_local_commit_and_remote(self):
        (self.local / 'recipe.txt').write_text('local\n')
        remote_head = self.advance_remote('recipe.txt', 'remote\n')
        with self.assertRaisesRegex(RuntimeError, 'File xung đột'):
            sync(inspect(self.local), 'local recipe update')
        self.assertEqual((self.local / 'recipe.txt').read_text(), 'local\n')
        self.assertFalse(run(self.local, 'status', '--porcelain').strip())
        self.assertEqual(run(self.local, 'log', '-1', '--format=%s').strip(), 'local recipe update')
        self.assertEqual(run(self.remote, 'rev-parse', 'main').strip(), remote_head)
        inspect(self.local)  # no unfinished merge remains

    def test_edit_after_preview_is_not_committed(self):
        (self.local / 'recipe.txt').write_text('first\n')
        preview = inspect(self.local)
        (self.local / 'recipe.txt').write_text('second\n')
        with self.assertRaisesRegex(ValueError, 'Project đã thay đổi'):
            sync(preview, 'update')
        self.assertEqual(run(self.local, 'rev-parse', 'HEAD').strip(), preview.head)

    def test_new_branch_and_repeat_sync(self):
        run(self.local, 'switch', '-c', '26.3-fabric')
        sync(inspect(self.local), '')
        sync(inspect(self.local), '')
        self.assertEqual(run(self.local, 'rev-parse', 'HEAD'), run(self.remote, 'rev-parse', '26.3-fabric'))

    def test_secret_file_is_not_added(self):
        (self.local / 'TokenGH.txt').write_text('dummy')
        with self.assertRaisesRegex(ValueError, 'thông tin đăng nhập'):
            inspect(self.local)
        self.assertFalse(run(self.local, 'diff', '--cached', '--name-only').strip())


if __name__ == '__main__':
    unittest.main()
