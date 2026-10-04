"""RandomCraft Release Studio — portable Windows desktop application."""
import os
from pathlib import Path
import queue
import sys
import threading
import tkinter as tk
from tkinter import filedialog, messagebox, scrolledtext, ttk
import webbrowser

from release_core import GitHub, build, find_java, git, properties, repository_name, required_java


def default_project():
    location = Path(sys.executable if getattr(sys, "frozen", False) else __file__).resolve().parent
    for parent in (location, *location.parents):
        if (parent / "gradle.properties").exists():
            return parent
        if (parent / "26.3-Fabric/gradle.properties").exists():
            return parent / "26.3-Fabric"
    return Path.cwd()


class Studio(tk.Tk):
    def __init__(self):
        super().__init__()
        self.title("RandomCraft · Release Studio")
        self.geometry("1060x900")
        self.minsize(900, 800)
        self.configure(bg="#10181e")
        self.events = queue.Queue()
        self.result = None
        self.busy = False
        self.release_url = ""
        self.widgets = []
        self.project = tk.StringVar(value=str(default_project()))
        self.jdk = tk.StringVar()
        self.repo = tk.StringVar()
        self.token = tk.StringVar(value=os.getenv("GH_TOKEN", os.getenv("GITHUB_TOKEN", "")))
        self.tag = tk.StringVar()
        self.release_name = tk.StringVar()
        self.draft = tk.BooleanVar(value=True)
        self.prerelease = tk.BooleanVar(value=False)
        self.status = tk.StringVar(value="Sẵn sàng")
        self.details = tk.StringVar()
        self.artifact = tk.StringVar(value="Chưa build trong phiên này")
        self._layout()
        self.load_project()
        self.project.trace_add("write", self.invalidate)
        self.protocol("WM_DELETE_WINDOW", self.close)
        self.after(100, self.poll)

    def _layout(self):
        style = ttk.Style(self)
        style.theme_use("clam")
        style.configure("TFrame", background="#10181e")
        style.configure("TLabel", background="#10181e", foreground="#e5ede9", font=("Segoe UI", 10))
        style.configure("Muted.TLabel", foreground="#99ada6")
        style.configure("Heading.TLabel", font=("Segoe UI Semibold", 23))
        style.configure("TLabelframe", background="#10181e", bordercolor="#30423b")
        style.configure("TLabelframe.Label", background="#10181e", foreground="#6ae3ac", font=("Segoe UI Semibold", 11))
        style.configure("TEntry", fieldbackground="#202f34", foreground="#f1f6f4", insertcolor="white", padding=5)
        style.configure("TButton", font=("Segoe UI Semibold", 10), padding=(14, 6), background="#2c433c", foreground="#e9f6ef")
        style.map("TButton", background=[("active", "#3e6655"), ("disabled", "#22302e")], foreground=[("disabled", "#71877e")])
        style.configure("Accent.TButton", background="#53dba0", foreground="#09241b")
        style.map("Accent.TButton", background=[("active", "#8becbe"), ("disabled", "#345147")])
        style.configure("TCheckbutton", background="#10181e", foreground="#dde9e3", font=("Segoe UI", 10))
        style.map("TCheckbutton", background=[("active", "#10181e")])
        outer = ttk.Frame(self, padding=18)
        outer.pack(fill="both", expand=True)
        ttk.Label(outer, text="RandomCraft", style="Heading.TLabel").pack(anchor="w")
        ttk.Label(outer, text="RELEASE STUDIO   /   Build mod và phát hành trên GitHub", style="Muted.TLabel").pack(anchor="w", pady=(2, 12))
        source = ttk.LabelFrame(outer, text="01  ·  Project & build", padding=14)
        source.pack(fill="x")
        source.columnconfigure(1, weight=1)
        self.field(source, 0, "Project", self.project, lambda: self.choose("project"))
        self.field(source, 1, "Java JDK", self.jdk, lambda: self.choose("jdk"))
        ttk.Label(source, textvariable=self.details, style="Muted.TLabel").grid(row=2, column=1, sticky="w", pady=(3, 0))
        actions = ttk.Frame(source)
        actions.grid(row=3, column=0, columnspan=3, sticky="ew", pady=(12, 0))
        self.button(actions, "Build JAR", self.start_build, "Accent.TButton").pack(side="left")
        self.button(actions, "Đọc lại project", self.load_project).pack(side="left", padx=8)
        self.button(actions, "Mở thư mục JAR", self.open_artifacts).pack(side="left")
        ttk.Label(source, textvariable=self.artifact, style="Muted.TLabel", wraplength=900).grid(row=4, column=0, columnspan=3, sticky="w", pady=(8, 0))

        release = ttk.LabelFrame(outer, text="02  ·  GitHub Release", padding=14)
        release.pack(fill="x", pady=14)
        release.columnconfigure(1, weight=1)
        self.field(release, 0, "Repository", self.repo)
        self.field(release, 1, "GitHub token", self.token, secret=True)
        ttk.Label(release, text="Token chỉ giữ trong bộ nhớ · Cần Contents: write (và Workflows: write nếu commit sửa workflow)", style="Muted.TLabel").grid(row=2, column=1, sticky="w")
        self.field(release, 3, "Tag", self.tag)
        self.field(release, 4, "Tên release", self.release_name)
        ttk.Label(release, text="Ghi chú").grid(row=5, column=0, sticky="nw", padx=(0, 14), pady=6)
        self.notes = tk.Text(release, height=3, bg="#202f34", fg="#eef5f0", insertbackground="white", relief="flat", font=("Segoe UI", 10), wrap="word", padx=8, pady=6)
        self.notes.grid(row=5, column=1, columnspan=2, sticky="ew", pady=5)
        self.widgets.append(self.notes)
        options = ttk.Frame(release)
        options.grid(row=6, column=1, sticky="w", pady=6)
        for label, variable in (("Bản nháp (draft)", self.draft), ("Pre-release", self.prerelease)):
            check = ttk.Checkbutton(options, text=label, variable=variable)
            check.pack(side="left", padx=(0, 20))
            self.widgets.append(check)
        row = ttk.Frame(release)
        row.grid(row=7, column=0, columnspan=3, sticky="ew", pady=(6, 0))
        self.button(row, "Kiểm tra kết nối", self.check_connection).pack(side="left")
        self.release_button = self.button(row, "Xem trước & tạo release", self.start_release, "Accent.TButton")
        self.release_button.pack(side="left", padx=8)
        self.open_release_button = self.button(row, "Mở release", self.open_release)
        self.open_release_button.pack(side="left")

        ttk.Label(outer, text="03  ·  Nhật ký", foreground="#6ae3ac").pack(anchor="w", pady=(0, 6))
        self.log = scrolledtext.ScrolledText(outer, height=7, bg="#091115", fg="#c4d9ce", insertbackground="white", relief="flat", font=("Consolas", 9), state="disabled", padx=12, pady=8)
        ttk.Label(outer, textvariable=self.status, style="Muted.TLabel").pack(side="bottom", anchor="w", pady=(10, 0))
        self.log.pack(fill="both", expand=True)

    def field(self, parent, row, label, variable, browse=None, secret=False):
        ttk.Label(parent, text=label).grid(row=row, column=0, sticky="w", padx=(0, 14), pady=3)
        entry = ttk.Entry(parent, textvariable=variable, show="•" if secret else "")
        entry.grid(row=row, column=1, columnspan=1 if browse else 2, sticky="ew", pady=3)
        self.widgets.append(entry)
        if browse:
            self.button(parent, "Chọn…", browse).grid(row=row, column=2, padx=(8, 0))

    def button(self, parent, text, command, style="TButton"):
        button = ttk.Button(parent, text=text, command=command, style=style)
        self.widgets.append(button)
        return button

    def invalidate(self, *_):
        self.result = None
        self.artifact.set("Chưa build trong phiên này")
        self.refresh_buttons()

    def refresh_buttons(self):
        self.release_button.configure(state="normal" if self.result and not self.busy else "disabled")
        self.open_release_button.configure(state="normal" if self.release_url and not self.busy else "disabled")

    def choose(self, kind):
        path = filedialog.askdirectory(title="Chọn thư mục project" if kind == "project" else "Chọn thư mục JDK")
        if path:
            (self.project if kind == "project" else self.jdk).set(path)
            if kind == "project":
                self.load_project()

    def load_project(self):
        try:
            project = Path(self.project.get()).resolve()
            props = properties(project)
            mc, version = props["minecraft_version"], props["mod_version"]
            name = props.get("archives_base_name", "randomcraft")
            if "randomcraft" not in name:
                raise ValueError("Chọn project RandomCraft.")
            loader = "neoforge" if "neoforge" in name.lower() else "forge" if "forge" in name.lower() else "fabric"
            self.details.set(f"Minecraft {mc}   ·   {loader.title()}   ·   Mod {version}   ·   JDK {required_java(project)}")
            self.jdk.set(find_java(project))
            self.tag.set(f"{mc}-{loader}-v{version}")
            self.release_name.set(f"RandomCraft {version} · Minecraft {mc} ({loader.title()})")
            self.prerelease.set(any(part in mc for part in ("snapshot", "pre", "rc")))
            try:
                self.repo.set(repository_name(git(project, "remote", "get-url", "origin")))
            except Exception:
                self.repo.set("")
            self.notes.delete("1.0", "end")
            self.notes.insert("1.0", f"RandomCraft {version} for Minecraft {mc} ({loader.title()}).\nRandomizes crafting recipes, including modded recipes.")
            self.invalidate()
            self.status.set("Sẵn sàng · Build JAR trước khi tạo release")
        except Exception as error:
            self.invalidate()
            self.status.set(str(error))

    def append_log(self, text):
        token = self.token.get().strip()
        if token:
            text = text.replace(token, "[redacted]")
        self.log.configure(state="normal")
        self.log.insert("end", text)
        self.log.see("end")
        self.log.configure(state="disabled")

    def task(self, label, function, kind):
        if self.busy:
            return
        self.busy = True
        self.status.set(label)
        for widget in self.widgets:
            widget.configure(state="disabled")
        def worker():
            try:
                self.events.put((kind, function()))
            except Exception as error:
                self.events.put(("error", str(error)))
            finally:
                self.events.put(("done", None))
        threading.Thread(target=worker, daemon=True).start()

    def poll(self):
        try:
            while True:
                kind, value = self.events.get_nowait()
                if kind == "log":
                    self.append_log(value)
                elif kind == "build":
                    self.result = value
                    self.artifact.set(f"✓ {value.jar.name}  ·  {value.jar.stat().st_size:,} bytes")
                    self.status.set("Build thành công · Sẵn sàng xem trước release")
                elif kind == "release":
                    self.release_url = value
                    self.append_log(f"Hoàn tất: {value}\n")
                    self.status.set("Tạo release thành công")
                elif kind == "connection":
                    self.status.set(f"Đã kết nối · {value['full_name']} · Có quyền ghi")
                elif kind == "error":
                    self.status.set("Thao tác thất bại · Xem log")
                    self.append_log(f"\nLỖI: {value}\n")
                    messagebox.showerror("Không thể hoàn tất", value, parent=self)
                elif kind == "done":
                    self.busy = False
                    for widget in self.widgets:
                        widget.configure(state="normal")
                    self.refresh_buttons()
        except queue.Empty:
            pass
        self.after(100, self.poll)

    def start_build(self):
        project, jdk = Path(self.project.get()), Path(self.jdk.get())
        self.invalidate()
        self.task("Đang build…", lambda: build(project, jdk, lambda line: self.events.put(("log", line))), "build")

    def check_connection(self):
        token, repo = self.token.get(), self.repo.get()
        self.task("Đang kiểm tra GitHub…", lambda: GitHub(token).check(repo), "connection")

    def start_release(self):
        if not self.result:
            return
        try:
            self.result.verify()
            repo = repository_name(self.repo.get())
            client = GitHub(self.token.get())
        except Exception as error:
            messagebox.showerror("Chưa sẵn sàng", str(error), parent=self)
            return
        result = self.result
        tag, title = self.tag.get().strip(), self.release_name.get().strip()
        notes = self.notes.get("1.0", "end").strip()
        draft, pre = self.draft.get(), self.prerelease.get()
        preview = (f"Repository: {repo}\nTag: {tag}\nTên: {title}\n"
                   f"Chế độ: {'Bản nháp' if draft else 'CÔNG KHAI'}{' · Pre-release' if pre else ''}\n"
                   f"JAR: {result.jar.name}\nSHA-256: {result.digest}\n\n{notes}\n\nTạo release với thông tin này?")
        if not messagebox.askokcancel("Xem trước GitHub Release", preview, parent=self):
            return
        self.task("Đang tạo release và upload JAR…", lambda: client.release(
            result, repo, tag, title, notes, draft, pre, lambda line: self.events.put(("log", line))), "release")

    def open_artifacts(self):
        path = Path(self.project.get()) / "build/libs"
        if path.exists():
            os.startfile(path)
        else:
            messagebox.showinfo("Chưa có JAR", "Hãy build project trước.", parent=self)

    def open_release(self):
        if self.release_url.startswith("https://github.com/"):
            webbrowser.open(self.release_url)

    def close(self):
        if self.busy:
            messagebox.showinfo("Đang xử lý", "Chờ thao tác hiện tại hoàn tất rồi đóng ứng dụng.", parent=self)
            return
        self.token.set("")
        self.destroy()


if __name__ == "__main__":
    Studio().mainloop()
