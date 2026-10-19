#!/bin/sh
# Compiles the pure-Swift logic (iosApp/Logic) with the host checks and runs them on macOS. No simulator/device.
set -e
cd "$(dirname "$0")/.."
OUT="${TMPDIR:-/tmp}/iosapp-checks"
swiftc -o "$OUT" iosApp/Logic/*.swift Checks/main.swift
"$OUT"
