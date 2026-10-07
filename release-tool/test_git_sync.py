import tempfile
import unittest
from pathlib import Path

from git_sync import inspect, run, sync, connect, prepare_many, sync_many


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

    def source_folder(self, name):
        folder = self.root / name
        folder.mkdir()
        (folder / 'build.gradle').write_text('// local build\n')
        (folder / 'gradle.properties').write_text('mod_version=1.2.0\n')
        (folder / 'recipe.txt').write_text('local updated recipe\n')
        return folder

    def test_connect_existing_branch_preserves_source_and_remote_ci(self):
        ci = self.other / '.github/workflows/release.yml'
        ci.parent.mkdir(parents=True)
        ci.write_text('name: Release\n')
        remote_head = self.advance_remote()
        folder = self.source_folder('import-existing')
        preview = connect(folder, str(self.remote), 'main')
        self.assertEqual(preview.head, remote_head)
        self.assertEqual((folder / 'recipe.txt').read_text(), 'local updated recipe\n')
        self.assertEqual((folder / '.github/workflows/release.yml').read_text(), 'name: Release\n')
        self.configure(folder)
        sync(preview, 'import updated source')
        run(folder, 'merge-base', '--is-ancestor', remote_head, 'HEAD')

    def test_connect_new_branch_excludes_builds_and_keeps_wrapper(self):
        folder = self.source_folder('legacy')
        (folder / 'build').mkdir()
        (folder / 'build/output.jar').write_bytes(b'dummy')
        (folder / 'TokenGH.txt').write_text('dummy')
        (folder / 'gradle/wrapper').mkdir(parents=True)
        (folder / 'gradle/wrapper/gradle-wrapper.jar').write_bytes(b'wrapper')
        preview = connect(folder, str(self.remote), '1.12.2-forge')
        self.assertEqual(preview.head, '')
        self.assertNotIn('build/output.jar', preview.paths)
        self.assertNotIn('TokenGH.txt', preview.paths)
        self.assertIn('gradle/wrapper/gradle-wrapper.jar', preview.paths)
        self.configure(folder)
        sync(preview, 'initial legacy version')
        self.assertEqual(run(folder, 'rev-parse', 'HEAD'), run(self.remote, 'rev-parse', '1.12.2-forge'))

    def test_failed_connection_does_not_create_git(self):
        folder = self.source_folder('failed')
        with self.assertRaises(RuntimeError):
            connect(folder, str(self.root / 'missing.git'), 'main')
        self.assertFalse((folder / '.git').exists())

    def test_batch_pushes_selected_branches_only(self):
        original = run(self.remote, 'rev-parse', 'main')
        a, b = self.source_folder('version-a'), self.source_folder('version-b')
        previews = prepare_many([(a, 'version-a'), (b, 'version-b')], str(self.remote))
        self.configure(a)
        self.configure(b)
        self.assertEqual(sync_many(previews, 'batch import'), ['version-a', 'version-b'])
        self.assertEqual(run(self.remote, 'rev-parse', 'main'), original)
        for preview in previews:
            self.assertEqual(run(preview.project, 'rev-parse', 'HEAD'), run(self.remote, 'rev-parse', preview.branch))

    def test_batch_detects_changed_later_project_before_any_push(self):
        a, b = self.source_folder('changed-a'), self.source_folder('changed-b')
        previews = prepare_many([(a, 'changed-a'), (b, 'changed-b')], str(self.remote))
        (b / 'recipe.txt').write_text('edited after preview\n')
        with self.assertRaisesRegex(ValueError, 'chưa push nhánh nào'):
            sync_many(previews, 'batch')
        self.assertFalse(run(self.remote, 'for-each-ref', 'refs/heads/changed-a').strip())

    def test_batch_rejects_wrong_branch_and_duplicate_targets(self):
        with self.assertRaisesRegex(ValueError, 'khác nhánh đích'):
            prepare_many([(self.local, 'wrong-branch')], str(self.remote))
        with self.assertRaisesRegex(ValueError, 'cùng một nhánh'):
            prepare_many([(self.local, 'main'), (self.other, 'main')], str(self.remote))

    def test_batch_stops_on_conflict_and_leaves_later_branch_unpushed(self):
        later = self.source_folder('later')
        later_preview = connect(later, str(self.remote), 'later')
        self.configure(later)
        (self.local / 'recipe.txt').write_text('conflicting local\n')
        first = inspect(self.local)
        remote_head = self.advance_remote('recipe.txt', 'conflicting remote\n')
        with self.assertRaisesRegex(RuntimeError, 'Chưa xử lý: later'):
            sync_many([first, later_preview], 'batch update')
        self.assertEqual(run(self.remote, 'rev-parse', 'main').strip(), remote_head)
        self.assertFalse(run(self.remote, 'for-each-ref', 'refs/heads/later').strip())
        self.assertEqual(inspect(later).head, '')


if __name__ == '__main__':
    unittest.main()
