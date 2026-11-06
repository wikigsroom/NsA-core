"""Durable facts. Mutations use revision compare-and-swap, including SQLite development."""
from __future__ import annotations
import json
import time
from sqlalchemy import BigInteger, Integer, String, Text, create_engine, event, select, update
from sqlalchemy.orm import DeclarativeBase, Mapped, mapped_column, sessionmaker

class Base(DeclarativeBase):
    pass

class Record(Base):
    __tablename__ = 'records'
    kind: Mapped[str] = mapped_column(String(40), primary_key=True)
    identity: Mapped[str] = mapped_column(String(160), primary_key=True)
    revision: Mapped[int] = mapped_column(Integer, default=1)
    state: Mapped[str] = mapped_column(String(40), default='draft', index=True)
    owner: Mapped[str] = mapped_column(String(200), default='')
    payload: Mapped[str] = mapped_column(Text, default='{}')
    created: Mapped[int] = mapped_column(BigInteger, default=lambda: int(time.time()))
    updated: Mapped[int] = mapped_column(BigInteger, default=lambda: int(time.time()))

    def data(self) -> dict:
        return json.loads(self.payload)

class Audit(Base):
    __tablename__ = 'audit'
    identity: Mapped[str] = mapped_column(String(40), primary_key=True)
    actor: Mapped[str] = mapped_column(String(200), index=True)
    action: Mapped[str] = mapped_column(String(120))
    target: Mapped[str] = mapped_column(String(200))
    request_id: Mapped[str] = mapped_column(String(50))
    occurred: Mapped[int] = mapped_column(BigInteger, index=True)
    payload: Mapped[str] = mapped_column(Text)

class Conflict(Exception):
    pass

def connect(url: str):
    engine = create_engine(url, pool_pre_ping=True, connect_args={'check_same_thread': False, 'timeout': 30} if url.startswith('sqlite') else {})
    if url.startswith('sqlite'):
        @event.listens_for(engine, 'connect')
        def configure(connection, _):
            connection.execute('PRAGMA journal_mode=WAL')
            connection.execute('PRAGMA foreign_keys=ON')
            connection.execute('PRAGMA busy_timeout=30000')
    return engine, sessionmaker(bind=engine, expire_on_commit=False)

def create(db, kind: str, identity: str, payload: dict, owner='', state='draft') -> Record:
    if db.get(Record, (kind, identity)):
        raise Conflict('Identity already exists')
    row = Record(kind=kind, identity=identity, payload=json.dumps(payload, separators=(',', ':')), owner=owner, state=state, revision=1)
    db.add(row)
    db.flush()
    return row

def change(db, row: Record, expected: int, data: dict, state: str | None = None) -> Record:
    now = int(time.time())
    result = db.execute(update(Record).where(Record.kind == row.kind, Record.identity == row.identity, Record.revision == expected).values(
        payload=json.dumps(data, separators=(',', ':')), revision=expected + 1, state=state or row.state, updated=now), execution_options={'synchronize_session': False})
    if result.rowcount != 1:
        raise Conflict('Revision changed; reload before retrying')
    db.expire(row)
    db.refresh(row)
    return row

def rows(db, kind: str):
    return list(db.scalars(select(Record).where(Record.kind == kind).order_by(Record.created.desc(), Record.identity)))
