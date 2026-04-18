"""Shared pytest fixtures."""
from __future__ import annotations

import os
from pathlib import Path

import pytest

FIXTURES_DIR = Path(__file__).parent / "fixtures"


@pytest.fixture
def tmp_data_dir(tmp_path: Path, monkeypatch: pytest.MonkeyPatch) -> Path:
    """Isolated HA_DATA_DIR per test."""
    monkeypatch.setenv("HA_DATA_DIR", str(tmp_path))
    return tmp_path
