#!/usr/bin/env python3
"""Deprecated. The AI sidecar lives in ../ai/server.py."""

from pathlib import Path
import runpy

runpy.run_path(str(Path(__file__).resolve().parents[1] / "ai" / "server.py"), run_name="__main__")
