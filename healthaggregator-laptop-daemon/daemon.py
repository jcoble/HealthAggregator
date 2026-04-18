"""FastAPI app exposing the sync API.

Phase 2 scope: /sync/health (unauthenticated) and /sync/version (authenticated).
Later phases add /sync/push, /sync/pull, /sync/migrate.
"""
from __future__ import annotations

import sqlite3
from pathlib import Path

from fastapi import Depends, FastAPI, HTTPException, Request, status

from config import Config, load_or_create_token


def _read_schema_version(db_path: Path) -> int:
    if not db_path.exists():
        return 0
    with sqlite3.connect(db_path) as conn:
        row = conn.execute("PRAGMA user_version").fetchone()
        return int(row[0]) if row else 0


def make_app() -> FastAPI:
    cfg = Config.from_env()
    token = load_or_create_token(cfg.token_path)
    app = FastAPI(title="HealthAggregator Sync Daemon", version=cfg.daemon_version)
    app.state.config = cfg
    app.state.token = token

    def require_token(request: Request) -> None:
        header = request.headers.get("authorization", "")
        if not header.startswith("Bearer "):
            raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="missing_bearer_token")
        supplied = header.removeprefix("Bearer ").strip()
        if supplied != app.state.token:
            raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="bad_token")

    @app.get("/sync/health")
    def health() -> dict:
        return {
            "status": "ok",
            "db_path": str(cfg.db_path),
            "db_exists": cfg.db_path.exists(),
        }

    @app.get("/sync/version", dependencies=[Depends(require_token)])
    def version() -> dict:
        return {
            "schema_version": _read_schema_version(cfg.db_path),
            "daemon_version": cfg.daemon_version,
        }

    return app


app = make_app()
