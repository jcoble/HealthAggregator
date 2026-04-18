"""Runtime configuration loaded from environment variables with sensible defaults."""
from __future__ import annotations

import os
import secrets
from dataclasses import dataclass
from pathlib import Path


@dataclass(frozen=True)
class Config:
    data_dir: Path
    db_path: Path
    token_path: Path
    bind_host: str
    bind_port: int
    daemon_version: str

    @staticmethod
    def from_env() -> "Config":
        data_dir = Path(os.environ.get("HA_DATA_DIR", str(Path.home() / "HealthAggregatorData")))
        return Config(
            data_dir=data_dir,
            db_path=data_dir / "healthaggregator.db",
            token_path=data_dir / "sync.token",
            bind_host=os.environ.get("HA_BIND_HOST", "0.0.0.0"),
            bind_port=int(os.environ.get("HA_BIND_PORT", "8719")),
            daemon_version="0.1.0",
        )


def load_or_create_token(token_path: Path) -> str:
    """Return the persisted token, creating it (and the parent directory) if absent."""
    if token_path.exists():
        return token_path.read_text().strip()
    token_path.parent.mkdir(parents=True, exist_ok=True)
    token = secrets.token_urlsafe(32)
    token_path.write_text(token)
    token_path.chmod(0o600)
    return token
