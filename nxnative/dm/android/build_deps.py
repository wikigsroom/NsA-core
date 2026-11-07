"""Read Clang Make dependencies without losing Windows or Unicode paths."""
from pathlib import Path
import re


def dependency_paths(text):
    text = re.sub(r"\\\r?\n", " ", text)
    # A drive colon is followed by a path, not dependency-list whitespace.
    separator = re.search(r"(?<!\\):(?=\s|$)", text)
    if separator is None:
        raise ValueError("Missing Make dependency separator")
    body = text[separator.end():]
    paths, token = [], []
    index = 0
    while index < len(body):
        char = body[index]
        if char == "\\" and index + 1 < len(body) and (body[index + 1].isspace() or body[index + 1] in "\\#:"):
            index += 1
            token.append(body[index])
        elif char == "$" and index + 1 < len(body) and body[index + 1] == "$":
            token.append("$")
            index += 1
        elif char == "#":
            end = body.find("\n", index)
            index = len(body) if end < 0 else end
            continue
        elif char.isspace():
            if token:
                paths.append("".join(token))
                token = []
        else:
            token.append(char)
        index += 1
    if token:
        paths.append("".join(token))
    if not paths:
        raise ValueError("Empty Make dependency list")
    return paths


def dep_stale(obj, dependency, source):
    try:
        stamp = obj.stat().st_mtime_ns
        if stamp < source.stat().st_mtime_ns:
            return True
        # Clang emits UTF-8 even when the Windows Python locale is cp936.
        text = dependency.read_text(encoding="utf-8")
        for name in dependency_paths(text):
            if Path(name).stat().st_mtime_ns > stamp:
                return True
    except (OSError, UnicodeError, ValueError):
        # Missing/unreadable dependencies must invalidate the cached object.
        return True
    return False
