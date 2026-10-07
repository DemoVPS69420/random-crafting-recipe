"""Review, commit, merge origin and push without rewriting remote history."""
from dataclasses import dataclass
from pathlib import Path
import hashlib
import os
import subprocess


def run(project, *args, timeout=180):
    env = os.environ.copy()
    env['GIT_TERMINAL_PROMPT'] = '0'
    result = subprocess.run(
        ['git', '-c', f'safe.directory={project.as_posix()}',
         '-c', 'credential.interactive=never', '-C', str(project), *args],
        capture_output=True, text=True, encoding='utf-8', errors='replace',
        timeout=timeout, env=env,
        creationflags=subprocess.CREATE_NO_WINDOW if os.name == 'nt' else 0)
    if result.returncode:
        raise RuntimeError(result.stderr.strip() or result.stdout.strip() or 'Git thất bại.')
    return result.stdout


@dataclass(frozen=True)
class Preview:
    project: Path
    branch: str
    remote: str
    head: str
    changes: tuple[str, ...]
    paths: tuple[str, ...]
    fingerprint: str


def inspect(project):
    project = Path(project).resolve()
    top = Path(run(project, 'rev-parse', '--show-toplevel').strip()).resolve()
    if top != project:
        raise ValueError('Chọn thư mục gốc Git của project.')
    for state in ('MERGE_HEAD', 'CHERRY_PICK_HEAD', 'REVERT_HEAD', 'rebase-merge', 'rebase-apply'):
        path = Path(run(project, 'rev-parse', '--git-path', state).strip())
        if (path if path.is_absolute() else project / path).exists():
            raise ValueError('Git đang merge/rebase hoặc xử lý commit dở dang. Hoàn tất thao tác đó trước.')
    branch = run(project, 'symbolic-ref', '--quiet', '--short', 'HEAD').strip()
    remote = run(project, 'remote', 'get-url', 'origin').strip()
    push_remote = run(project, 'remote', 'get-url', '--push', 'origin').strip()
    if push_remote != remote:
        raise ValueError('Origin đang dùng địa chỉ fetch và push khác nhau. Kiểm tra lại remote trước.')
    if '://' in remote and '@' in remote.split('://', 1)[1]:
        raise ValueError('Hãy dùng Git Credential Manager thay cho token trong URL origin.')
    head = run(project, 'rev-parse', 'HEAD').strip()
    status = run(project, 'status', '--porcelain=v1', '-z', '--untracked-files=all', '--no-renames')
    changes = tuple(x for x in status.split('\0') if x)
    paths = tuple(x[3:] for x in changes)
    digest = hashlib.sha256(status.encode())
    for path in paths:
        name = Path(path).name.lower()
        if (name == 'tokengh.txt' or name.startswith('.env') or
                name.endswith(('.pem', '.key', '.p12', '.pfx')) or
                name in ('id_rsa', 'id_ed25519', 'credentials.json')):
            raise ValueError(f'File có thể chứa thông tin đăng nhập: {path}. Loại khỏi commit trước.')
        file = project / path
        if file.is_symlink() or file.is_dir():
            raise ValueError(f'Cần xử lý riêng symlink/submodule: {path}')
        if file.is_file():
            digest.update(file.read_bytes())
    # Include staged changes even if the working file was subsequently restored.
    digest.update(run(project, 'diff', '--cached', '--binary', '--no-ext-diff').encode())
    return Preview(project, branch, remote, head, changes, paths, digest.hexdigest())


def sync(preview, message, log=lambda text: None):
    project = preview.project
    if inspect(project) != preview:
        raise ValueError('Project đã thay đổi sau khi xem trước. Bấm Commit & Push lại để kiểm tra.')
    if preview.paths and not message.strip():
        raise ValueError('Nhập nội dung commit.')
    branch_ref = f'refs/heads/{preview.branch}'
    tracking_ref = f'refs/remotes/origin/{preview.branch}'
    log(f'Đang tải nhánh {preview.branch} từ origin…\n')
    exists = bool(run(project, 'ls-remote', '--heads', 'origin', branch_ref).strip())
    if exists:
        run(project, 'fetch', '--no-tags', 'origin', f'{branch_ref}:{tracking_ref}')
    if inspect(project) != preview:
        raise ValueError('Project thay đổi trong lúc tải dữ liệu. Chưa commit/push; hãy kiểm tra lại.')
    if preview.paths:
        run(project, 'add', '--', *preview.paths)
        run(project, 'commit', '-m', message.strip())
        log('Đã lưu commit local.\n')
    if exists:
        try:
            run(project, 'merge', '--no-edit', tracking_ref)
        except RuntimeError as error:
            conflicts = run(project, 'diff', '--name-only', '--diff-filter=U').strip()
            merge_head = Path(run(project, 'rev-parse', '--git-path', 'MERGE_HEAD').strip())
            if (merge_head if merge_head.is_absolute() else project / merge_head).exists():
                run(project, 'merge', '--abort')
            raise RuntimeError('Không thể gộp origin. Commit local vẫn được giữ; chưa push.\n'
                               + (f'File xung đột:\n{conflicts}' if conflicts else str(error))) from None
    if run(project, 'status', '--porcelain').strip():
        raise RuntimeError('Có thay đổi mới trong workspace; chưa push. Kiểm tra rồi thử lại.')
    log('Đang push lên origin…\n')
    try:
        run(project, 'push', '--set-upstream', 'origin', f'HEAD:{branch_ref}')
    except RuntimeError as error:
        raise RuntimeError('Chưa push thành công. Commit local đã được lưu. '
                           'Nếu origin vừa có commit mới, bấm Commit & Push lại.\n' + str(error)) from None
    commit = run(project, 'rev-parse', '--short', 'HEAD').strip()
    log(f'Đã đồng bộ {preview.branch} · {commit}\n')
    return commit
