"""Read-only conformance checks for the documentation baseline, not the App.

Run with a task-local Python environment and requirements-validation.txt.
No network, Android, Docker, WSL or browser is invoked. A report is written only
when --report is supplied. Production implementations need independent tests.
"""
from __future__ import annotations

import argparse
import base64
import copy
import hashlib
import json
import re
import sys
import unicodedata
from collections import Counter
from datetime import datetime, timezone
from html.parser import HTMLParser
from pathlib import Path
from urllib.parse import unquote, urlsplit

REPO = Path(__file__).resolve().parents[2]
DOCS = REPO / "docs"
CONTRACTS = DOCS / "engineering/contracts"
MAX_LONG = 2**63 - 1
CHUNK = 8 * 1024 * 1024
CHECKS: list[dict] = []


def require(condition, message):
    if not condition:
        raise ValueError(message)


def strict_json(raw):
    def pairs(items):
        result = {}
        for key, value in items:
            require(key not in result, f"duplicate JSON key: {key}")
            result[key] = value
        return result

    def invalid_constant(value):
        raise ValueError(f"non-finite JSON number: {value}")

    return json.loads(raw, object_pairs_hook=pairs, parse_constant=invalid_constant)


def read(path):
    return strict_json(Path(path).read_text("utf-8"))


def sha(raw):
    return hashlib.sha256(raw).hexdigest()


def count(value):
    require(isinstance(value, str) and re.fullmatch(r"0|[1-9][0-9]{0,18}", value), "invalid ByteCount")
    number = int(value)
    require(number <= MAX_LONG, "ByteCount exceeds signed 64-bit")
    return number


def safe_path(value):
    require(isinstance(value, str) and 0 < len(value.encode("utf-8")) <= 1024, "path byte limit")
    require(not value.startswith("/") and "\\" not in value, "absolute/backslash path")
    require(not any(ord(c) < 32 or ord(c) == 127 for c in value), "control character path")
    parts = value.split("/")
    require(len(parts) <= 32 and all(x not in ("", ".", "..") for x in parts), "unsafe path segment/depth")
    reserved = {"con", "prn", "aux", "nul"} | {f"{x}{n}" for x in ("com", "lpt") for n in range(1, 10)}
    for part in parts:
        require(not any(c in '<>:"|?*' for c in part), "Windows-unstable path")
        require(not part.endswith((" ", ".")), "trailing dot/space path")
        require(part.split(".")[0].casefold() not in reserved, "reserved path")
    return unicodedata.normalize("NFC", value).casefold()


def file_semantics(data, save=False):
    rows = data["files"]
    normalized = [safe_path(row["path"]) for row in rows]
    require(len(normalized) == len(set(normalized)), "duplicate/normalized path")
    names = set(normalized)
    for value in names:
        parts = value.split("/")
        require(not any("/".join(parts[:i]) in names for i in range(1, len(parts))), "file used as parent directory")
    total = sum(count(row["bytes"]) for row in rows)
    require(total <= MAX_LONG, "file sum overflow")
    require(total == count(data["total_bytes" if save else "unpacked_bytes"]), "file byte sum mismatch")
    if save:
        require(total <= 2 * 1024**3 and len(rows) <= 10000, "save expanded size/file limit")
    else:
        require(data["file_count"] == len(rows) <= 250000, "file count mismatch")


def chunk_semantics(data):
    total = count(data["bytes"])
    require(total > 0 and count(data["chunk_size_bytes"]) == CHUNK, "chunk/object size")
    rows = data["chunks"]
    require(len(rows) == (total + CHUNK - 1) // CHUNK, "chunk count mismatch")
    for i, row in enumerate(rows):
        require(row["index"] == i and count(row["offset_bytes"]) == i * CHUNK, "chunk gap/order")
        require(count(row["length_bytes"]) == min(CHUNK, total - i * CHUNK), "chunk length mismatch")


def transfer_semantics(data):
    require(count(data["durable_bytes"]) <= count(data["total_bytes"]), "durable exceeds total")


def upload_completion_semantics(grant, completion, total_bytes):
    require(grant["mode"] == "multipart", "not multipart")
    require(grant["part_count"] == (count(total_bytes) + count(grant["part_bytes"]) - 1) // count(grant["part_bytes"]), "upload part count")
    require([p["part_number"] for p in completion["parts"]] == list(range(1, grant["part_count"]+1)), "missing/duplicate/out-of-order upload parts")


def evidence_semantics(data):
    if data["result"] != "verified":
        return
    require(data["duration_seconds"] >= 1800 and data["save_reload_passed"] and data["input_passed"], "incomplete verified evidence")
    target = data["target_fps"]
    average, p95, low = (27, 50, 20) if target == 30 else (54, 25, 40)
    require(data["average_fps"] >= average and data["frame_p95_ms"] <= p95 and data["low1_fps"] >= low, "performance below verified default")
    require(0.95 <= data["game_speed_ratio"] <= 1.05, "game logic speed below verified default")
    require(all(k in data["capability"] for k in ("ram_bytes", "page_size_bytes", "gpu_family", "gpu_driver_digest", "vulkan_api")), "incomplete verified capability")


def verify_envelope(data, trusted_key):
    from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PublicKey
    require(data["algorithm"] == "Ed25519" and data["key_id"] == trusted_key["key_id"], "untrusted key/algorithm")
    require(data["purpose"] == trusted_key["purpose"], "key purpose mismatch")
    raw = base64.b64decode(data["payload_base64"], validate=True)
    require(len(raw) <= 2 * 1024**2 and sha(raw) == data["payload_sha256"], "payload size/digest mismatch")
    message = b"NSA/" + data["purpose"].encode("ascii") + b"/v1\0" + raw
    key = Ed25519PublicKey.from_public_bytes(base64.b64decode(trusted_key["public_key_base64"], validate=True))
    key.verify(base64.b64decode(data["signature_base64"], validate=True), message)
    return strict_json(raw.decode("utf-8"))


def reject(name, callback):
    from cryptography.exceptions import InvalidSignature
    from jsonschema import ValidationError

    try:
        callback()
    except (ValueError, ValidationError, InvalidSignature) as error:
        return {"case": name, "result": "rejected", "exception": type(error).__name__}
    raise ValueError(f"negative vector accepted: {name}")


class Anchors(HTMLParser):
    def __init__(self):
        super().__init__()
        self.ids = set()
        self.links = []

    def handle_starttag(self, tag, attrs):
        attrs = dict(attrs)
        if "id" in attrs:
            self.ids.add(attrs["id"])
        for key in ("href", "src"):
            if key in attrs:
                self.links.append(attrs[key])


def check(name, callback):
    try:
        details = callback()
        CHECKS.append({"name": name, "status": "passed", "details": details})
    except Exception as error:
        CHECKS.append({"name": name, "status": "failed", "error": f"{type(error).__name__}: {error}"})


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--deps", type=Path, help="Task-local Python package directory")
    parser.add_argument("--report", type=Path, help="Explicit output under project task root")
    parser.add_argument("--protected-snapshot", type=Path, help="Optional path/hash map of unrelated working files")
    parser.add_argument("--presentation-report", type=Path, help="Optional actual browser review of the current handbook")
    args = parser.parse_args()
    if args.deps:
        sys.path.insert(0, str(args.deps.resolve()))
    from jsonschema import Draft202012Validator, FormatChecker
    from referencing import Registry, Resource
    from openapi_spec_validator import OpenAPIV31SpecValidator
    import markdown

    schemas = {p.name: read(p) for p in CONTRACTS.glob("*.schema.json")}
    registry = Registry().with_resources((s["$id"], Resource.from_contents(s)) for s in schemas.values())
    validators = {n: Draft202012Validator(s, registry=registry, format_checker=FormatChecker()) for n, s in schemas.items()}

    def validate(name, value, definition=None):
        if definition:
            schema = {"$ref": schemas[name]["$id"] + "#/$defs/" + definition}
            Draft202012Validator(schema, registry=registry, format_checker=FormatChecker()).validate(value)
        else:
            validators[name].validate(value)

    def traceability():
        requirements = read(DOCS / "product/data/requirements.json")["requirements"]
        acceptance = read(DOCS / "product/data/acceptance.json")
        screens = read(DOCS / "design/obsidian-portrait/data/screens.json")["screens"]
        case_map = {x["id"]: x for x in acceptance["original_cases"] + acceptance["supplemental_cases"]}
        ids = {r["id"] for r in requirements}
        pages = {s["id"] for s in screens}
        require(len(ids) == len(requirements) == 90, "requirement identity/count")
        require(Counter(r["priority"] for r in requirements) == {"P0": 85, "P1": 5}, "requirement priority")
        expected_groups = {"APP": 8, "CAT": 10, "DL": 15, "PKG": 9, "RUN": 12, "SAVE": 8, "UPD": 8, "OPS": 10, "NFR": 10}
        require(ids == {f"{group}-{i:02}" for group, n in expected_groups.items() for i in range(1, n+1)}, "stable requirement IDs")
        require(len(pages) == 32 and sum(len(s["variants"]) for s in screens) == 73, "design catalogue changed")
        require({s["id"] for s in screens if s["orientation"] == "landscape"} == {"S09", "S10", "S11"}, "orientation boundary")
        require(len(acceptance["original_cases"]) == 58 and len(acceptance["supplemental_cases"]) == 20 and len(case_map) == 78, "case identity/count")
        prd = (DOCS / "product/游戏大厅二次开发需求说明书.md").read_text("utf-8")
        detail = (DOCS / "product/产品需求细则.md").read_text("utf-8")
        plan = (DOCS / "product/开发计划与验收矩阵.md").read_text("utf-8")
        interactions = (DOCS / "product/页面与交互验收规格.md").read_text("utf-8")
        table = {}
        for line in prd.splitlines():
            if re.match(r"^\| (?:APP|CAT|DL|PKG|RUN|SAVE|UPD|OPS|NFR)-\d{2} \|", line):
                cells = [c.strip() for c in line.strip().strip("|").split("|")]
                table[cells[0]] = (cells[1], cells[2])
        require(set(table) == ids, "PRD table IDs")
        work = set(re.findall(r"^\| (T-\d{2}) ", plan, re.M))
        require(work == {f"T-{i:02}" for i in range(1, 25)}, "work item IDs")
        for row in requirements:
            require(table[row["id"]] == (row["priority"], row["statement"]), f"PRD statement mismatch {row['id']}")
            require([r["id"] for r in row["rules"]] == [row["id"]+f".{i:02}" for i in range(1, 5)], "subrule IDs")
            require(all(r["id"] in detail and r["text"] in detail for r in row["rules"]), "subrule text mismatch")
            require(row["acceptance"] and set(row["acceptance"]) <= case_map.keys(), "unmapped acceptance")
            require(set(row["supplemental_acceptance"]) <= case_map.keys(), "unmapped CON")
            require(row["work_items"] and set(row["work_items"]) <= work, "unmapped task")
            require(row["screens"] and set(row["screens"]) <= pages, "unmapped page")
            require((DOCS / "product/data" / row["technical_document"]).resolve().is_file(), "missing technical chapter")
            for cid in row["acceptance"] + row["supplemental_acceptance"]:
                require(row["id"] in case_map[cid]["requirements"], f"reverse case mismatch {cid}")
        for case in case_map.values():
            require(set(case["requirements"]) <= ids and case["implementation_status"] == "not_run", "case references or false acceptance")
            require(re.search(r"^\| " + re.escape(case["id"]) + r" \|", plan, re.M), "case missing from plan")
        require(len(re.findall(r"^\| INT-\d{2} \|", interactions, re.M)) == 10, "supplemental interactions")
        return {"requirements": 90, "subrules": 360, "screens": 32, "typical_states": 73, "work_items": 24, "original_cases": 58, "supplemental_cases": 20, "supplemental_interactions": 10}

    check("requirement_and_acceptance_traceability", traceability)

    def schema_check():
        for schema in schemas.values():
            Draft202012Validator.check_schema(schema)
        return {"draft": "2020-12", "schemas": len(schemas), "resolver": "local registry only"}

    check("json_schema_definitions", schema_check)
    api = read(CONTRACTS / "openapi.json")

    def api_check():
        from jsonschema_path import SchemaPath
        from urllib.request import url2pathname

        def deny_remote(uri):
            raise ValueError("remote resolution forbidden: " + uri)

        def load_local(uri):
            path = Path(url2pathname(urlsplit(uri).path)).resolve()
            require(path.is_relative_to(CONTRACTS), "external local API reference")
            return read(path)

        spec = SchemaPath.from_dict(api, base_uri=(CONTRACTS / "openapi.json").as_uri(),
                                    handlers={"file": load_local, "http": deny_remote,
                                              "https": deny_remote, "<all_urls>": deny_remote})
        OpenAPIV31SpecValidator(spec).validate()
        ids = {r["id"] for r in read(DOCS / "product/data/requirements.json")["requirements"]}
        operations = []
        for path, verbs in api["paths"].items():
            for method, operation in verbs.items():
                if method not in ("get", "post", "put", "patch", "delete"):
                    continue
                operations.append(operation["operationId"])
                require(set(operation["x-requirements"]) <= ids, "API requirement reference")
                require(bool(operation.get("security")) == (path not in ("/v1/bootstrap", "/v1/credentials/refresh")), "API auth boundary")
                if path.startswith("/admin/"):
                    require(operation.get("x-allowed-roles"), "missing server RBAC")
                if method != "get":
                    require({"$ref": "#/components/parameters/IdempotencyKey"} in operation["parameters"], "missing mutation idempotency")
        require(len(operations) == len(set(operations)) == 43 and len(api["paths"]) == 40, "API inventory mismatch")
        return {"openapi": api["openapi"], "paths": len(api["paths"]), "operations": len(operations), "auth_and_idempotency": "checked"}

    check("openapi_and_operation_guards", api_check)

    def refs_check():
        seen = 0

        def walk(node, document):
            nonlocal seen
            if isinstance(node, list):
                for child in node:
                    walk(child, document)
            elif isinstance(node, dict):
                if "$ref" in node:
                    target, _, fragment = node["$ref"].partition("#")
                    require(not urlsplit(target).scheme, "unexpected remote JSON ref")
                    resolved = (document.parent / target).resolve() if target else document
                    require(resolved.is_relative_to(CONTRACTS), "JSON ref outside contract directory")
                    value = read(resolved)
                    for part in fragment.lstrip("/").split("/") if fragment else []:
                        value = value[unquote(part).replace("~1", "/").replace("~0", "~")]
                    seen += 1
                for child in node.values():
                    walk(child, document)

        for path in [CONTRACTS / "openapi.json", *CONTRACTS.glob("*.schema.json")]:
            walk(read(path), path.resolve())
        return {"resolved_local_references": seen}

    check("offline_json_references", refs_check)
    examples = CONTRACTS / "examples"

    def examples_check():
        mapping = {"release-manifest": "release-manifest", "release-envelope": "signed-envelope", "file-index": "file-index", "chunk-index": "chunk-index", "runtime-registry": "runtime-registry", "compiled-slot-identity": "compiled-slot-identity", "save-backup": "save-backup", "ready-marker": "local-records", "install-journal": "local-records", "runtime-event": "runtime-event", "policy": "policy", "app-update": "app-update", "transfer-record": "local-records", "save-commit": "local-records"}
        for name, schema in mapping.items():
            validate(schema + ".schema.json", read(examples / (name + ".json")))
        api_examples = read(examples / "api-examples.json")
        for example in api_examples["examples"]:
            validate("domain.schema.json", example["value"], example["definition"])
        example_by_type = {example["definition"]: example["value"] for example in api_examples["examples"]}
        upload_completion_semantics(example_by_type["ArtifactUploadGrant"], example_by_type["ArtifactUploadComplete"], "10737418240")
        release = read(examples / "release-manifest.json")
        files = read(examples / "file-index.json")
        chunks = read(examples / "chunk-index.json")
        file_semantics(files)
        file_semantics(read(examples / "save-backup.json"), save=True)
        chunk_semantics(chunks)
        transfer_semantics(read(examples / "transfer-record.json"))
        for label in ("file_index", "chunk_index"):
            raw = (examples / (label.replace("_", "-") + ".json")).read_bytes()
            require(release[label]["sha256"] == sha(raw) and count(release[label]["bytes"]) == len(raw), "index raw-byte binding")
        require(release["release_id"] == files["release_id"] and release["unpacked_bytes"] == files["unpacked_bytes"] and release["file_count"] == files["file_count"], "manifest/file binding")
        require(release["archive"]["object_id"] == chunks["object_id"] and release["archive"]["bytes"] == chunks["bytes"], "manifest/chunk binding")
        slots = read(examples / "runtime-registry.json")["slots"]
        compiled_identity = read(examples / "compiled-slot-identity.json")
        require(compiled_identity == {"identity_schema": 1, **{key: value for key, value in slots[0].items() if key != "library_sha256"}}, "compiled identity/registry binding")
        require(len({s["runtime_slot_id"] for s in slots}) == len(slots) == len({s["library_name"] for s in slots}), "slot/library uniqueness")
        slot = next(s for s in slots if s["runtime_slot_id"] == release["runtime"]["runtime_slot_id"])
        require(slot["profile_digest"] == release["runtime"]["profile_digest"], "profile binding")
        require(set(release["runtime"]["module_build_ids"]) <= set(slot["supported_module_build_ids"]), "module binding")
        require(release["runtime"]["shader_chain_id"] == slot["shader_chain_id"] and release["runtime"]["shader_bundle_version"] in slot["shader_bundle_versions"], "shader binding")
        require(set(release["runtime"]["approved_driver_ids"]) <= set(slot["approved_driver_ids"]), "driver binding")
        ready = read(examples / "ready-marker.json")
        require(ready["manifest_sha256"] == sha((examples / "release-manifest.json").read_bytes()), "READY manifest binding")
        require(ready["file_index_sha256"] == release["file_index"]["sha256"], "READY index binding")
        key = read(examples / "fixture-public-key.json")
        require(key["production_trust_root"] is False, "fixture key must not be production")
        require(verify_envelope(read(examples / "release-envelope.json"), key) == release, "signed manifest mismatch")
        return {"schema_valid_examples": len(mapping) + len(api_examples["examples"]), "cross_object_bindings": "passed", "signature": "real Ed25519 test vector; no private key saved", "game_archive": "absent by design"}

    check("positive_examples_and_signed_byte_bindings", examples_check)

    def negatives_check():
        vectors = []
        def bad(name, callback):
            vectors.append(reject(name, callback))

        bad("duplicate_json_key", lambda: strict_json('{"x":1,"x":2}'))
        bad("non_finite_number", lambda: strict_json('{"x":NaN}'))
        for value in ("-1", "01", "1e3", str(MAX_LONG+1)):
            bad("invalid_byte_count_"+value, lambda value=value: count(value))
        require(count("9007199254740993") == 9007199254740993, "precision loss above JS safe integer")
        for value in ("../save", "/absolute", "C:/outside", "a\\b", "a//b", "a/./b", "a/../b", "CON.txt", "a\x00b", "x/"*33+"file", "字"*342):
            bad("unsafe_path_"+repr(value), lambda value=value: safe_path(value))
        data = read(examples / "file-index.json")
        data["file_count"] += 1
        bad("file_count_mismatch", lambda: file_semantics(data))
        duplicate = read(examples / "file-index.json")
        duplicate["files"].append(copy.deepcopy(duplicate["files"][0]))
        bad("duplicate_file", lambda: file_semantics(duplicate))
        chunks = read(examples / "chunk-index.json")
        chunks["chunks"][0]["offset_bytes"] = "1"
        bad("chunk_gap", lambda: chunk_semantics(chunks))
        chunks2 = read(examples / "chunk-index.json")
        chunks2["chunks"][0]["length_bytes"] = "0"
        bad("chunk_length", lambda: chunk_semantics(chunks2))
        transfer = read(examples / "transfer-record.json")
        transfer["durable_bytes"] = str(count(transfer["total_bytes"])+1)
        bad("durable_progress_over_total", lambda: transfer_semantics(transfer))
        feedback = {"category": "app", "description": "short"}
        bad("feedback_too_short", lambda: validate("domain.schema.json", feedback, "FeedbackRequest"))
        api_examples = {x["definition"]: x["value"] for x in read(examples / "api-examples.json")["examples"]}
        weak_etag = copy.deepcopy(api_examples["DownloadGrant"])
        weak_etag["etag"] = 'W/"weak"'
        bad("weak_range_validator", lambda: validate("domain.schema.json", weak_etag, "DownloadGrant"))
        incomplete = copy.deepcopy(api_examples["ArtifactUploadComplete"])
        incomplete["parts"].pop()
        bad("multipart_incomplete", lambda: upload_completion_semantics(api_examples["ArtifactUploadGrant"], incomplete, "10737418240"))
        duplicate_part = copy.deepcopy(api_examples["ArtifactUploadComplete"])
        duplicate_part["parts"][1]["part_number"] = 1
        bad("multipart_duplicate_part", lambda: upload_completion_semantics(api_examples["ArtifactUploadGrant"], duplicate_part, "10737418240"))
        oversized_save = read(examples / "save-backup.json")
        oversized_save["total_bytes"] = str(2 * 1024**3 + 1)
        oversized_save["files"][0]["bytes"] = oversized_save["total_bytes"]
        bad("save_expanded_limit", lambda: file_semantics(oversized_save, save=True))
        verified = {"result": "verified", "duration_seconds": 60, "save_reload_passed": False, "input_passed": True}
        bad("insufficient_playability_evidence", lambda: evidence_semantics(verified))
        event = read(examples / "runtime-event.json")
        event["event_type"] = "save_committed"
        bad("save_event_without_commit", lambda: validate("runtime-event.schema.json", event))
        key = read(examples / "fixture-public-key.json")
        envelope = read(examples / "release-envelope.json")
        changed = copy.deepcopy(envelope)
        raw = base64.b64decode(changed["payload_base64"])
        raw = raw[:-2] + bytes([raw[-2] ^ 1]) + raw[-1:]
        changed["payload_base64"] = base64.b64encode(raw).decode("ascii")
        changed["payload_sha256"] = sha(raw)  # Defeat hash-only checks; signature must reject.
        bad("tampered_payload_with_updated_hash", lambda: verify_envelope(changed, key))
        wrong_purpose = copy.deepcopy(envelope)
        wrong_purpose["purpose"] = "policy"
        purpose_key = {**key, "purpose": "policy"}
        bad("signature_domain_separation", lambda: verify_envelope(wrong_purpose, purpose_key))
        wrong_key = {**key, "key_id": "unknown-root"}
        bad("unknown_trust_root", lambda: verify_envelope(envelope, wrong_key))
        return {"rejected_negative_vectors": len(vectors), "vectors": vectors, "integer_precision_positive": "passed"}

    check("boundary_and_tampering_negative_vectors", negatives_check)

    def state_check():
        states = read(CONTRACTS / "state-machines.json")
        domain = schemas["domain.schema.json"]["$defs"]
        for name, definition in (("transfer", "TransferState"), ("install", "InstallState"), ("session", "SessionState"), ("release", "ReleaseState")):
            machine = states["machines"][name]
            require(machine["states"] == domain[definition]["enum"], "state DTO mismatch")
            require(machine["initial"] in machine["states"] and set(machine["terminal"]) <= set(machine["states"]), "initial/terminal state")
            require(all(len(edge) == 2 and set(edge) <= set(machine["states"]) for edge in machine["transitions"]), "invalid state edge")
            reached = {machine["initial"]}
            for _ in machine["states"]:
                reached.update(b for a, b in machine["transitions"] if a in reached)
            require(reached == set(machine["states"]), "unreachable state")
        require(domain["Blocker"]["enum"] == states["blockers"], "blocker mismatch")
        require(schemas["runtime-event.schema.json"]["properties"]["event_type"]["enum"] == states["phase_events"], "runtime event mismatch")
        errors = read(CONTRACTS / "errors.json")["errors"]
        require(len({x["code"] for x in errors}) == len(errors) == 50, "error inventory")
        require(domain["ErrorCode"]["enum"] == [x["code"] for x in errors], "error DTO mismatch")
        return {"state_machines": 4, "error_codes": len(errors), "event_types": len(states["phase_events"])}

    check("state_and_error_contract_consistency", state_check)

    def links_check():
        paths = [*sorted((DOCS / "product").rglob("*.md")), *sorted((DOCS / "engineering").rglob("*.md")), DOCS / "design/obsidian-portrait/README.md", DOCS / "design/obsidian-portrait/07-handoff.md"]
        anchor_cache = {}
        count_links = 0
        for path in paths:
            source = path.read_text("utf-8")
            require(len(re.findall(r"^```", source, re.M)) % 2 == 0, f"unclosed code fence: {path.name}")
            require(not any(line.rstrip() != line for line in source.splitlines()), f"trailing whitespace: {path.name}")
            parsed = Anchors()
            parsed.feed(markdown.markdown(source, extensions=["tables", "fenced_code", "toc"]))
            for href in parsed.links:
                url = urlsplit(href)
                if url.scheme or url.netloc:
                    continue
                target = (path.parent / unquote(url.path)).resolve() if url.path else path.resolve()
                require(target.is_file(), f"broken link {path.relative_to(REPO)} -> {href}")
                if url.fragment and target.suffix in (".md", ".html"):
                    if target not in anchor_cache:
                        other = Anchors()
                        text = target.read_text("utf-8")
                        other.feed(markdown.markdown(text, extensions=["tables", "fenced_code", "toc"]) if target.suffix == ".md" else text)
                        anchor_cache[target] = other.ids
                    require(unquote(url.fragment) in anchor_cache[target], f"missing anchor {path.name} -> {href}")
                count_links += 1
        return {"markdown_documents": len(paths), "local_links": count_links}

    check("markdown_links_and_structure", links_check)

    if args.presentation_report:
        def presentation_check():
            review = read(args.presentation_report)
            require(review.get("result") == "passed" and review.get("browser_closed") and review.get("file_protocol"), "incomplete handbook browser review")
            require(not review["external_requests"], "handbook requested external resources")
            handbook = DOCS / "product/产品与技术开发基线.html"
            require(sha(handbook.read_bytes()) == review["document_sha256"], "browser review is for an older handbook")
            require({view["width"] for view in review["views"]} == {1360, 390}, "missing presentation viewport")
            require(all(view["result"] == "passed" and view["articles"] == 14 and not view["duplicate_ids"] and not view["broken_anchors"] and not view["broken_images"] for view in review["views"]), "presentation failure")
            return {"viewports": [1360, 390], "direct_offline_file": True, "external_requests": 0, "browser_closed": True, "document_sha256": review["document_sha256"]}
        check("offline_handbook_browser_review", presentation_check)

    if args.protected_snapshot:
        def protected_check():
            original = read(args.protected_snapshot)
            require(all(sha((REPO / name).read_bytes()) == digest for name, digest in original.items()), "unrelated working file changed")
            return {"unchanged_files": len(original)}
        check("protected_runtime_work_preserved", protected_check)

    report = {"baseline": "2.0", "checked_at_utc": datetime.now(timezone.utc).isoformat(), "scope": "documentation and contract conformance only", "app_service_game_acceptance": "not executed; implementation gates remain open", "network_required_for_checks": False, "checks": CHECKS, "passed": all(x["status"] == "passed" for x in CHECKS)}
    if args.report:
        target = args.report.resolve()
        require(target.is_relative_to(REPO.parent), "report must stay within task root")
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", "utf-8", newline="\n")
    for item in CHECKS:
        print(item["status"].upper() + " " + item["name"] + (": " + item["error"] if "error" in item else ""))
    print(f"{sum(c['status'] == 'passed' for c in CHECKS)}/{len(CHECKS)} groups passed; App/device/service acceptance not executed.")
    return 0 if report["passed"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
