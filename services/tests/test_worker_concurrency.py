from concurrent.futures import ThreadPoolExecutor
import threading
import time
from catalog.db import Record, change, create
import worker.main as worker


def pending(platform):
    with platform.sessions() as db:
        create(db, 'release', 'lease-release', {'verification_operation_id': 'lease-operation'}, state='verifying')
        create(db, 'operation', 'lease-operation', {'target_id': 'lease-release', 'lease_until': 0, 'attempts': 0}, state='pending')
        db.commit()


def test_expensive_verification_retains_lease(system, monkeypatch):
    platform, _, _ = system
    pending(platform)
    entered, finish, renewed = threading.Event(), threading.Event(), threading.Event()
    monkeypatch.setattr(worker, 'LEASE_SECONDS', 2)
    monkeypatch.setattr(worker, 'RENEW_SECONDS', .1)
    original = worker.Lease.renew
    def renew(lease):
        result = original(lease); renewed.set(); return result
    monkeypatch.setattr(worker.Lease, 'renew', renew)
    def verify(*_):
        entered.set()
        if not finish.wait(15): raise TimeoutError('Test verification was not released')
        return {'checked': True}, {'release_id': 'lease-release'}
    monkeypatch.setattr(worker, 'verify', verify)
    with ThreadPoolExecutor(max_workers=1) as pool:
        task = pool.submit(worker.run_once, platform)
        try:
            assert entered.wait(10)
            assert renewed.wait(10)
            time.sleep(2.2)  # Outlive the original claim; a second worker must not steal it.
            assert worker.run_once(platform) is False
        finally:
            finish.set()
        assert task.result(timeout=10)
    with platform.sessions() as db:
        row = db.get(Record, ('operation', 'lease-operation'))
        assert row.state == 'succeeded' and row.data()['attempts'] == 1


def test_stale_worker_cannot_overwrite_new_owner(system, monkeypatch):
    platform, _, _ = system
    pending(platform)
    def verify(*_):
        with platform.sessions() as db:
            row = db.get(Record, ('operation', 'lease-operation'))
            change(db, row, row.revision, {**row.data(), 'lease_owner': 'new-owner'}, 'running')
            db.commit()
        return {}, {'release_id': 'lease-release'}
    monkeypatch.setattr(worker, 'verify', verify)
    assert worker.run_once(platform)
    with platform.sessions() as db:
        assert db.get(Record, ('operation', 'lease-operation')).data()['lease_owner'] == 'new-owner'
        assert db.get(Record, ('release', 'lease-release')).state == 'verifying'
        assert db.get(Record, ('manifest', 'lease-release')) is None


def test_admin_change_during_verification_is_preserved(system, monkeypatch):
    platform, _, _ = system
    pending(platform)
    def verify(*_):
        with platform.sessions() as db:
            row = db.get(Record, ('release', 'lease-release'))
            change(db, row, row.revision, row.data(), 'blocked'); db.commit()
        return {}, {'release_id': 'lease-release'}
    monkeypatch.setattr(worker, 'verify', verify)
    assert worker.run_once(platform)
    with platform.sessions() as db:
        assert db.get(Record, ('release', 'lease-release')).state == 'blocked'
        assert db.get(Record, ('operation', 'lease-operation')).data()['error'] == 'ReleaseChanged'
        assert db.get(Record, ('manifest', 'lease-release')) is None


def test_expensive_api_operation_does_not_block_health(system, monkeypatch):
    platform, client, tokens = system
    entered, finish = threading.Event(), threading.Event()
    original = platform.dispatch
    # Use the contract's actual operation identity rather than an invented endpoint.
    from catalog.contracts import OPENAPI
    operation_id = OPENAPI['paths']['/admin/v1/health-summary']['get']['operationId']
    def slow(db, operation, *args):
        if operation == operation_id:
            entered.set()
            if not finish.wait(10): raise TimeoutError('Test request was not released')
        return original(db, operation, *args)
    monkeypatch.setattr(platform, 'dispatch', slow)
    with ThreadPoolExecutor(max_workers=2) as pool:
        expensive = pool.submit(client.get, '/admin/v1/health-summary', headers={'Authorization': 'Bearer ' + tokens['viewer']})
        try:
            assert entered.wait(5)
            health = pool.submit(client.get, '/healthz')
            assert health.result(timeout=3).status_code == 200
        finally:
            finish.set()
        assert expensive.result(timeout=5).status_code == 200
