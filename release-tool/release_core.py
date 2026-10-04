"""Build and release operations. No GUI dependencies and no saved credentials."""
from __future__ import annotations

import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import urllib.error
import urllib.parse
import urllib.request
import zipfile
from dataclasses import dataclass

NO_WINDOW = subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0


def properties(project: Path) -> dict[str, str]:
    result = {}
    for line in (project / "gradle.properties").read_text(encoding="utf-8-sig").splitlines():
        if "=" in line and not line.lstrip().startswith(("#", "!")):
            key, value = line.split("=", 1)
            result[key.strip()] = value.strip()
    return result


def source_digest(project: Path) -> str:
    files = [p for folder in ("src", "gradle") for p in (project / folder).rglob("*") if p.is_file()]
    files += [p for p in project.iterdir() if p.is_file() and (
        p.suffix in (".gradle", ".kts", ".properties") or p.name in ("LICENSE", "gradlew", "gradlew.bat"))]
    digest = hashlib.sha256()
    for path in sorted(files):
        digest.update(path.relative_to(project).as_posix().encode())
        digest.update(b"\0")
        digest.update(path.read_bytes())
    return digest.hexdigest()


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def git(project: Path, *args: str) -> str:
    # Trust only the explicitly selected local checkout, without changing global Git settings.
    result = subprocess.run(["git", "-c", f"safe.directory={project.as_posix()}", "-C", str(project), *args],
                            capture_output=True, text=True, encoding="utf-8", errors="replace",
                            creationflags=NO_WINDOW, timeout=30)
    if result.returncode:
        raise RuntimeError(result.stderr.strip() or "Git command failed")
    return result.stdout.strip()


def repository_name(value: str) -> str:
    value = value.strip().removesuffix("/").removesuffix(".git")
    for prefix in ("https://github.com/", "git@github.com:"):
        if value.startswith(prefix):
            value = value[len(prefix):]
    if not re.fullmatch(r"[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+", value):
        raise ValueError("Repository phải có dạng owner/repo hoặc URL GitHub.")
    return value


def required_java(project: Path) -> int:
    mc = properties(project).get("minecraft_version", "")
    if mc.startswith("26."):
        return 25
    if mc.startswith("1.21"):
        return 21
    if mc == "1.12.2":
        return 8
    return 17


def find_java(project: Path) -> str:
    major = required_java(project)
    candidates = []
    for parent in (project, *list(project.parents)[:3]):
        candidates.extend(sorted((parent / ".tools").glob(f"jdk-{major}*"), reverse=True))
    if os.environ.get("JAVA_HOME"):
        candidates.append(Path(os.environ["JAVA_HOME"]))
    return str(next((p for p in candidates if (p / "bin/java.exe").exists()), ""))


@dataclass(frozen=True)
class BuildResult:
    project: Path
    jar: Path
    digest: str
    source: str
    version: str
    minecraft: str

    def verify(self):
        if not self.jar.exists() or sha256(self.jar) != self.digest:
            raise ValueError("JAR đã thay đổi hoặc bị xóa. Hãy build lại.")
        if source_digest(self.project) != self.source:
            raise ValueError("Source đã thay đổi sau lần build. Hãy build lại.")


def build(project: Path, java_home: Path, log) -> BuildResult:
    project = project.resolve()
    props = properties(project)
    java = java_home / "bin" / ("java.exe" if os.name == "nt" else "java")
    if not java.is_file() or not (java_home / "bin" / ("javac.exe" if os.name == "nt" else "javac")).is_file():
        raise ValueError("Chọn thư mục JDK (có bin/java và bin/javac).")
    version = subprocess.run([str(java), "-version"], capture_output=True, text=True,
                             creationflags=NO_WINDOW, timeout=15)
    match = re.search(r'version "(?:1\.)?(\d+)', version.stderr + version.stdout)
    if not match or int(match[1]) != required_java(project):
        raise ValueError(f"Project này cần JDK {required_java(project)}.\n{version.stderr}")
    wrapper = project / "gradle/wrapper/gradle-wrapper.jar"
    if not wrapper.is_file():
        raise ValueError("Thiếu Gradle wrapper JAR.")
    before = source_digest(project)
    env = os.environ.copy()
    # Never pass GitHub credentials to the build process or Gradle plugins.
    for key in ("GH_TOKEN", "GITHUB_TOKEN"):
        env.pop(key, None)
    env["JAVA_HOME"] = str(java_home)
    env["PATH"] = str(java_home / "bin") + os.pathsep + env.get("PATH", "")
    # JDK 25 on Windows can fail to create Unix sockets in long user temp paths.
    if os.name == "nt":
        socket_dir = project / ".gradle" / "sockets"
        socket_dir.mkdir(parents=True, exist_ok=True)
        if len(str(socket_dir)) < 65:
            env["JAVA_TOOL_OPTIONS"] = (env.get("JAVA_TOOL_OPTIONS", "") +
                f' -Djdk.net.unixdomain.tmpdir="{socket_dir}"').strip()
    command = [str(java), "-classpath", str(wrapper), "org.gradle.wrapper.GradleWrapperMain",
               "clean", "build", "--no-daemon", "--console=plain"]
    log(f"Build {project.name} · JDK {required_java(project)}\n")
    with subprocess.Popen(command, cwd=project, env=env, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                          text=True, encoding="utf-8", errors="replace", creationflags=NO_WINDOW) as process:
        for line in process.stdout:
            log(line)
        code = process.wait()
    if code:
        raise RuntimeError(f"Build thất bại (exit {code}). Xem log để biết chi tiết.")
    if source_digest(project) != before:
        raise ValueError("Source thay đổi trong lúc build. Hãy build lại.")
    jars = [p for p in (project / "build/libs").glob("*.jar")
            if not p.name.endswith(("-sources.jar", "-dev.jar", "-javadoc.jar"))]
    if len(jars) != 1:
        raise ValueError(f"Cần đúng 1 JAR phát hành, tìm thấy {len(jars)}.")
    with zipfile.ZipFile(jars[0]) as archive:
        if "fabric.mod.json" in archive.namelist():
            metadata = json.loads(archive.read("fabric.mod.json"))
            if metadata["id"] != "randomcraft" or metadata["version"] != props["mod_version"]:
                raise ValueError("Metadata JAR không khớp project RandomCraft.")
    result = BuildResult(project, jars[0], sha256(jars[0]), before,
                         props["mod_version"], props["minecraft_version"])
    log(f"\nJAR: {result.jar}\nSHA-256: {result.digest}\n")
    return result


class GitHubError(RuntimeError):
    def __init__(self, status: int, message: str):
        super().__init__(f"GitHub HTTP {status}: {message}")
        self.status = status


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


class GitHub:
    def __init__(self, token: str):
        if not token.strip():
            raise ValueError("Nhập GitHub token có quyền Contents: write cho repository.")
        self.token = token.strip()

    def request(self, method, url, data=None, binary=False):
        if not url.startswith("https://") or urllib.parse.urlsplit(url).netloc not in (
                "api.github.com", "uploads.github.com"):
            raise ValueError("GitHub API URL không hợp lệ.")
        headers = {"Authorization": f"Bearer {self.token}", "Accept": "application/vnd.github+json",
                   "X-GitHub-Api-Version": "2026-03-10", "User-Agent": "RandomCraft-Release-Studio/1.0"}
        if data is not None:
            headers["Content-Type"] = "application/java-archive" if binary else "application/json"
            if not binary:
                data = json.dumps(data).encode()
        request = urllib.request.Request(url, data=data, headers=headers, method=method)
        try:
            with urllib.request.build_opener(NoRedirect).open(request, timeout=120) as response:
                return json.load(response)
        except urllib.error.HTTPError as error:
            try:
                message = json.loads(error.read()).get("message", error.reason)
            except (ValueError, OSError):
                message = str(error.reason)
            raise GitHubError(error.code, str(message).replace(self.token, "[redacted]")) from None

    def check(self, repo: str):
        repo = repository_name(repo)
        result = self.request("GET", f"https://api.github.com/repos/{repo}")
        if not result.get("permissions", {}).get("push", False):
            raise ValueError("Tài khoản/token chưa có quyền ghi repository này.")
        return result

    def release(self, result: BuildResult, repo: str, tag: str, title: str, notes: str,
                draft: bool, prerelease: bool, log):
        result.verify()
        repo = repository_name(repo)
        if not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9._-]{0,127}", tag) or ".." in tag or tag.endswith("."):
            raise ValueError("Tag chỉ dùng chữ, số, dấu chấm, gạch ngang và gạch dưới.")
        if not title.strip():
            raise ValueError("Nhập tên release.")
        if git(result.project, "status", "--porcelain"):
            raise ValueError("Commit các thay đổi và push nhánh trước khi release. Sau đó build lại nếu source thay đổi.")
        if repository_name(git(result.project, "remote", "get-url", "origin")) != repo:
            raise ValueError("Repository đích không khớp remote origin của project.")
        commit = git(result.project, "rev-parse", "HEAD")
        base = f"https://api.github.com/repos/{repo}"
        self.check(repo)
        self.request("GET", f"{base}/commits/{commit}")  # source must already be on GitHub
        try:
            self.request("GET", f"{base}/git/ref/tags/{urllib.parse.quote(tag, safe='')}")
        except GitHubError as error:
            if error.status != 404:
                raise
        else:
            raise ValueError("Tag đã tồn tại. Hãy dùng tag mới; công cụ không ghi đè release cũ.")
        # Drafts do not always create a tag yet, so check them separately (paginated).
        page = 1
        while True:
            releases = self.request("GET", f"{base}/releases?per_page=100&page={page}")
            if any(r["tag_name"] == tag for r in releases):
                raise ValueError("Release hoặc bản nháp dùng tag này đã tồn tại. Chọn tag mới.")
            if len(releases) < 100:
                break
            page += 1
        result.verify()
        jar_bytes = result.jar.read_bytes()
        if hashlib.sha256(jar_bytes).hexdigest() != result.digest:
            raise ValueError("JAR thay đổi trước lúc upload. Hãy build lại.")
        body = notes.rstrip() + f"\n\nMinecraft: {result.minecraft}\nCommit: {commit}\nSHA-256: `{result.digest}`\n"
        # Publish only after upload succeeds, so public releases never start without an asset.
        release = self.request("POST", f"{base}/releases", {
            "tag_name": tag, "target_commitish": commit, "name": title.strip(), "body": body,
            "draft": True, "prerelease": prerelease})
        url = release["html_url"]
        log(f"Đã tạo bản nháp: {url}\n")
        try:
            upload = release["upload_url"].split("{", 1)[0]
            asset = self.request("POST", upload + "?" + urllib.parse.urlencode({"name": result.jar.name}),
                                 jar_bytes, binary=True)
            if asset.get("state") != "uploaded" or asset.get("size") != len(jar_bytes):
                raise RuntimeError("GitHub chưa xác nhận upload đầy đủ.")
            if not draft:
                release = self.request("PATCH", f"{base}/releases/{release['id']}",
                                       {"draft": False, "make_latest": "false"})
        except Exception as error:
            raise RuntimeError(f"Release có thể chưa hoàn tất. Kiểm tra bản nháp trước khi thử lại: {url}\n{error}") from None
        return release["html_url"]
