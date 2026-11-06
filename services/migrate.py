"""Explicit schema bootstrap for PostgreSQL or local SQLite; run before production workers."""
from catalog.config import Settings
from catalog.db import Base, connect

if __name__ == '__main__':
    engine, _ = connect(Settings.load().database_url)
    Base.metadata.create_all(engine)
    print('Schema version 1 installed. Existing records were retained.')
