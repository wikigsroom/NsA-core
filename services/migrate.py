"""Explicit schema bootstrap for PostgreSQL or local SQLite; run before production workers."""
from catalog.config import Settings
from catalog.db import Base, connect
from catalog.policy import initialize_policy_sequence

if __name__ == '__main__':
    engine, sessions = connect(Settings.load().database_url)
    Base.metadata.create_all(engine)
    initialize_policy_sequence(sessions)
    print('Schema version 1 and policy sequence installed. Existing records were retained.')
