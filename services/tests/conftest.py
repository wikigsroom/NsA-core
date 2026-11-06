from __future__ import annotations
from dataclasses import replace
from pathlib import Path
import os
import secrets
import sys
import pytest
from fastapi.testclient import TestClient

ROOT = Path(__file__).resolve().parents[2]
(ROOT / 'output/hall').mkdir(parents=True, exist_ok=True)
sys.path.insert(0, str(ROOT)); sys.path.insert(0, str(ROOT / 'services'))
from catalog.config import Settings
from catalog.app import create_app
from catalog.crypto import token_hash
from catalog.db import create

@pytest.fixture
def system(tmp_path, monkeypatch):
    monkeypatch.setenv('NSA_MODE', 'test')
    monkeypatch.setenv('NSA_LOCAL_ROOT', str(tmp_path))
    monkeypatch.setenv('NSA_PUBLIC_URL', 'https://testserver')
    app = create_app(Settings.load())
    tokens = {'editor': secrets.token_urlsafe(48), 'reviewer': secrets.token_urlsafe(48), 'viewer': secrets.token_urlsafe(48)}
    roles = {'editor': ['editor', 'publisher', 'rights_reviewer', 'support'], 'reviewer': ['reviewer', 'rights_reviewer'], 'viewer': ['viewer']}
    with app.state.platform.sessions() as db:
        for actor, token in tokens.items(): create(db, 'dev_admin', token_hash(token), {'subject': actor, 'roles': roles[actor]}, state='active')
        db.commit()
    with TestClient(app, base_url='https://testserver') as client:
        yield app.state.platform, client, tokens
