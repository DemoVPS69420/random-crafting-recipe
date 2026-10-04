$ErrorActionPreference = 'Stop'
Push-Location $PSScriptRoot
try {
    python -m venv .venv
    if ($LASTEXITCODE -ne 0) { throw 'Cannot create Python virtual environment' }
    & .\.venv\Scripts\python.exe -m pip install -r requirements-build.txt
    if ($LASTEXITCODE -ne 0) { throw 'Cannot install build dependencies' }
    & .\.venv\Scripts\python.exe -m PyInstaller --noconfirm --clean --onefile --windowed --name RandomCraft-Release-Studio release_gui.py
    if ($LASTEXITCODE -ne 0) { throw 'Packaging failed' }
} finally {
    Pop-Location
}
