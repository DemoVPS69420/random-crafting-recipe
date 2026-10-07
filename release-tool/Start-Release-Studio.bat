@echo off
cd /d "%~dp0"
python release_gui.py
if errorlevel 1 pause
