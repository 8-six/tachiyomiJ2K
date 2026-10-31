#!/usr/bin/env python3
"""Bump versionName/versionCode in buildSrc/src/main/kotlin/Dependencies.kt.

Usage: bump_version.py [patch|minor|major] [path-to-Dependencies.kt]

A patch bump past .10 rolls over to the next minor (1.8.10 -> 1.9.0). Fails instead of
guessing when either declaration is missing, duplicated, or not in the expected format.
Prints the new versionName on stdout.
"""
import re
import sys

KIND = sys.argv[1] if len(sys.argv) > 1 else "patch"
PATH = sys.argv[2] if len(sys.argv) > 2 else "buildSrc/src/main/kotlin/Dependencies.kt"
MAX_PATCH = 10

CODE_RE = re.compile(r'(const val versionCode = )(\d+)')
NAME_RE = re.compile(r'(const val versionName = ")(\d+)\.(\d+)\.(\d+)(")')


def fail(message):
    sys.exit(f"bump_version: {message}")


if KIND not in ("patch", "minor", "major"):
    fail(f"unknown bump kind '{KIND}'")

text = open(PATH, encoding="utf-8").read()
if len(CODE_RE.findall(text)) != 1:
    fail("expected exactly one 'const val versionCode = <number>'")
if len(NAME_RE.findall(text)) != 1:
    fail("expected exactly one 'const val versionName = \"x.y.z\"'")

major, minor, patch = (int(g) for g in NAME_RE.search(text).groups()[1:4])
if KIND == "major":
    major, minor, patch = major + 1, 0, 0
elif KIND == "minor":
    minor, patch = minor + 1, 0
elif patch >= MAX_PATCH:
    minor, patch = minor + 1, 0
else:
    patch += 1

new_name = f"{major}.{minor}.{patch}"
text = CODE_RE.sub(lambda m: f"{m.group(1)}{int(m.group(2)) + 1}", text)
text = NAME_RE.sub(lambda m: f'{m.group(1)}{new_name}{m.group(5)}', text)
open(PATH, "w", encoding="utf-8").write(text)
print(new_name)
