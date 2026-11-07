"""Generate and verify the compiled identity of a unified Android runtime slot.

The metadata shares the generated header and translation unit with get_info.
It detects stale build inputs before collection; runtime verification still
checks the actual ABI and get_info result on the Android device.
"""
from __future__ import annotations
import json
from pathlib import Path
import re
import struct

FIELDS = ("runtime_slot_id", "library_name", "runtime_api_version", "core_revision",
          "profile_digest", "supported_module_build_ids", "shader_chain_id",
          "shader_bundle_versions", "approved_driver_ids", "settings_schema_version")


def identity_record(identity):
    record = {"identity_schema": 1, **{key: identity[key] for key in FIELDS}}
    if not isinstance(record["library_name"], str) or not re.fullmatch(r"libnsa_[a-z0-9_]+\.so", record["library_name"]):
        raise ValueError("Invalid compiled slot library name")
    for key in ("runtime_slot_id", "core_revision", "profile_digest", "shader_chain_id"):
        if not isinstance(record[key], str) or not 1 <= len(record[key]) <= 256 or "\0" in record[key]:
            raise ValueError("Invalid compiled identity field: " + key)
    for key in ("runtime_api_version", "settings_schema_version"):
        if type(record[key]) is not int or record[key] != 1:
            raise ValueError("Unsupported compiled identity version: " + key)
    for key in ("supported_module_build_ids", "approved_driver_ids"):
        values = record[key]
        if not isinstance(values, list) or not values or any(not isinstance(v, str) or not v or "\0" in v for v in values) or len(values) != len(set(values)):
            raise ValueError("Invalid compiled identity list: " + key)
    versions = record["shader_bundle_versions"]
    if not isinstance(versions, list) or not versions or any(type(v) is not int or v < 1 for v in versions) or len(versions) != len(set(versions)):
        raise ValueError("Invalid compiled shader versions")
    return record


def write_identity_header(path, identity, suffix=""):
    record = identity_record(identity)
    fields = {"NSA_SLOT_ID": record["runtime_slot_id"], "NSA_CORE_REVISION": record["core_revision"],
              "NSA_PROFILE_SHA256": record["profile_digest"],
              "NSA_MODULE_IDS": json.dumps(record["supported_module_build_ids"], separators=(",", ":")),
              "NSA_SHADER_CHAIN": record["shader_chain_id"],
              "NSA_RUNTIME_IDENTITY_JSON": json.dumps(record, sort_keys=True, separators=(",", ":"))}
    text = "\n".join("#define " + key + " " + json.dumps(value) for key, value in fields.items()) + "\n" + suffix
    path = Path(path)
    if not path.exists() or path.read_text(encoding="utf-8") != text:
        path.write_text(text, encoding="utf-8")


def _unique_object(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError("Duplicate compiled identity key")
        result[key] = value
    return result


def _reject_constant(value):
    raise ValueError("Non-finite compiled identity: " + value)


def verify_compiled_identity(image, expected):
    def region(offset, length):
        if offset < 0 or length < 0 or offset + length > len(image):
            raise ValueError("Truncated compiled runtime ELF")
        return image[offset:offset + length]

    header = struct.unpack("<16sHHIQQQIHHHHHH", region(0, 64))
    ident, elf_type, machine, version = header[:4]
    if ident[:7] != b"\x7fELF\x02\x01\x01" or elf_type != 3 or machine != 183 or version != 1:
        raise ValueError("Expected an AArch64 little-endian shared runtime")
    offset, entry_size, count, strings_index = header[6], header[11], header[12], header[13]
    if entry_size != 64 or not count or strings_index >= count:
        raise ValueError("Unsupported runtime ELF section table")
    table = region(offset, entry_size * count)
    sections = [struct.unpack_from("<IIQQQQIIQQ", table, i * 64) for i in range(count)]
    strings = sections[strings_index]
    if strings[1] != 3:
        raise ValueError("Invalid runtime section-name table")
    names = region(strings[4], strings[5])

    def string_at(data, start):
        if start < 0 or start >= len(data) or b"\0" not in data[start:]:
            raise ValueError("Invalid runtime ELF string")
        return data[start:data.index(b"\0", start)]

    metadata = [section for section in sections if string_at(names, section[0]) == b".nsa_identity"]
    if len(metadata) != 1:
        raise ValueError("Missing or duplicate compiled slot identity; rebuild the selected runtime")
    section = metadata[0]
    if section[1] != 1 or not section[2] & 2 or not 1 < section[5] <= 16384:
        raise ValueError("Invalid compiled identity section")
    payload = region(section[4], section[5])
    if payload[-1:] != b"\0" or b"\0" in payload[:-1]:
        raise ValueError("Invalid compiled identity terminator")
    actual = json.loads(payload[:-1].decode("utf-8"), object_pairs_hook=_unique_object,
                        parse_constant=_reject_constant)
    wanted = identity_record(expected)
    if not isinstance(actual, dict) or set(actual) != set(wanted) or type(actual.get("identity_schema")) is not int or actual["identity_schema"] != 1:
        raise ValueError("Unsupported compiled identity document")
    identity_record(actual)
    if actual != wanted:
        changed = [key for key in wanted if actual.get(key) != wanted[key]]
        raise ValueError("Compiled slot identity differs from registry: " + ", ".join(changed))

    dynamics = [s for s in sections if s[1] == 6]
    if len(dynamics) != 1:
        raise ValueError("Invalid runtime ELF dynamic table")
    dynamic = dynamics[0]
    if dynamic[6] >= count or dynamic[9] != 16 or dynamic[5] % 16:
        raise ValueError("Invalid runtime ELF dynamic entries")
    dynamic_strings = sections[dynamic[6]]
    if dynamic_strings[1] != 3:
        raise ValueError("Invalid runtime dynamic strings")
    raw_strings = region(dynamic_strings[4], dynamic_strings[5])
    raw_dynamic = region(dynamic[4], dynamic[5])
    sonames = [string_at(raw_strings, value).decode("ascii") for tag, value in struct.iter_unpack("<qQ", raw_dynamic) if tag == 14]
    if sonames != [wanted["library_name"]]:
        raise ValueError("Compiled runtime SONAME differs from its selected library")
    return actual
