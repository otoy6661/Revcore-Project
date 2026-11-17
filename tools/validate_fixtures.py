#!/usr/bin/env python3
"""Validate RevCore synthetic frame fixtures.

Checks, without third-party dependencies:

* every JSON file parses as UTF-8;
* every K-Line frame declares a length that matches its actual size;
* the last byte equals the additive two's-complement checksum of the rest;
* the header is one of the documented Honda headers;
* declared payload slices match the frame bytes where the fixture states them;
* ASCII payload text matches the payload bytes where stated.

This tool validates fixture consistency only. It does not prove that an ECU
accepts a request, and it does not validate vehicle compatibility.

Exit code 0 = all checks passed, 1 = at least one failure, 2 = usage error.
"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path
from typing import Any, Iterable

ACCEPTED_HEADERS = {0x72, 0x02, 0x7E, 0x7C, 0xFE, 0x7D, 0x7B}
MIN_FRAME = 4
MAX_FRAME = 0xFE


class Checker:
    def __init__(self, verbose: bool = False) -> None:
        self.verbose = verbose
        self.checked_frames = 0
        self.failures: list[str] = []

    def fail(self, path: Path, message: str) -> None:
        self.failures.append(f"{path.name}: {message}")

    def note(self, message: str) -> None:
        if self.verbose:
            print(f"  ok  {message}")

    @staticmethod
    def parse_hex(label: str, text: str) -> bytes:
        cleaned = text.strip()
        if not cleaned:
            raise ValueError(f"{label}: string hex kosong")
        parts = cleaned.split()
        if len(parts) == 1 and len(parts[0]) % 2 == 0 and len(parts[0]) > 2:
            # Allow a continuous hex string as well as space-separated bytes.
            parts = [parts[0][i : i + 2] for i in range(0, len(parts[0]), 2)]
        out = bytearray()
        for part in parts:
            if len(part) != 2:
                raise ValueError(f"{label}: token '{part}' bukan satu byte")
            try:
                out.append(int(part, 16))
            except ValueError as exc:
                raise ValueError(f"{label}: token '{part}' bukan hex") from exc
        return bytes(out)

    @staticmethod
    def checksum(frame: bytes) -> int:
        return (256 - (sum(frame[:-1]) & 0xFF)) & 0xFF

    def frame(
        self,
        path: Path,
        label: str,
        text: str,
        *,
        expected_payload: str | None = None,
        expected_ascii: str | None = None,
        expected_declared: int | None = None,
        payload_start: int = 3,
    ) -> bytes | None:
        try:
            frame = self.parse_hex(label, text)
        except ValueError as exc:
            self.fail(path, str(exc))
            return None

        self.checked_frames += 1
        if not (MIN_FRAME <= len(frame) <= MAX_FRAME):
            self.fail(path, f"{label}: ukuran {len(frame)} di luar {MIN_FRAME}..{MAX_FRAME}")
            return None
        if frame[0] not in ACCEPTED_HEADERS:
            self.fail(path, f"{label}: header 0x{frame[0]:02X} tidak termasuk header terdokumentasi")
        if expected_declared is not None and frame[1] != expected_declared:
            self.fail(
                path,
                f"{label}: byte panjang 0x{frame[1]:02X} != declaredLength {expected_declared}",
            )
        if frame[1] != len(frame):
            self.fail(
                path,
                f"{label}: panjang deklarasi 0x{frame[1]:02X} ({frame[1]}) "
                f"!= ukuran sebenarnya {len(frame)}",
            )
            return None
        expected = self.checksum(frame)
        if expected != frame[-1]:
            self.fail(
                path,
                f"{label}: checksum 0x{frame[-1]:02X} salah, seharusnya 0x{expected:02X}",
            )
        if expected_payload is not None:
            try:
                payload = self.parse_hex(f"{label}.payloadHex", expected_payload)
            except ValueError as exc:
                self.fail(path, str(exc))
                return frame
            actual = frame[payload_start:-1]
            if len(frame) <= payload_start:
                self.fail(path, f"{label}: frame terlalu pendek untuk memiliki payload")
            elif actual != payload:
                self.fail(
                    path,
                    f"{label}: payloadHex {payload.hex(' ').upper()} "
                    f"!= potongan frame {actual.hex(' ').upper()}",
                )
        if expected_ascii is not None:
            payload = frame[payload_start:-1]
            try:
                decoded = payload.decode("ascii")
            except UnicodeDecodeError:
                self.fail(path, f"{label}: payload bukan ASCII, tidak dapat dibandingkan dengan payloadAscii")
                return frame
            if decoded != expected_ascii:
                self.fail(
                    path,
                    f"{label}: payloadAscii '{expected_ascii}' != payload '{decoded}'",
                )
        self.note(f"{label}: {len(frame)} byte, checksum 0x{frame[-1]:02X}")
        return frame

    def load(self, path: Path) -> Any | None:
        try:
            with path.open("r", encoding="utf-8") as handle:
                return json.load(handle)
        except (OSError, UnicodeDecodeError, json.JSONDecodeError) as exc:
            self.fail(path, f"JSON tidak dapat dibaca: {exc}")
            return None

    def walk_strings(self, value: Any, key: str = "") -> Iterable[tuple[str, str]]:
        if isinstance(value, dict):
            for child_key, child in value.items():
                yield from self.walk_strings(child, child_key)
        elif isinstance(value, list):
            for child in value:
                yield from self.walk_strings(child, key)
        elif isinstance(value, str):
            yield key, value


def validate_requests(checker: Checker, path: Path) -> None:
    data = checker.load(path)
    if not isinstance(data, dict):
        return
    frames = data.get("frames")
    if not isinstance(frames, list):
        checker.fail(path, "tidak memiliki array 'frames'")
        return
    for index, entry in enumerate(frames):
        if not isinstance(entry, dict) or "hex" not in entry:
            checker.fail(path, f"frames[{index}] tidak memiliki 'hex'")
            continue
        label = entry.get("nama", f"frames[{index}]")
        checker.frame(path, label, str(entry["hex"]))


def validate_identification(checker: Checker, path: Path) -> None:
    data = checker.load(path)
    if not isinstance(data, dict):
        return
    entries = data.get("contoh")
    if not isinstance(entries, list):
        checker.fail(path, "tidak memiliki array 'contoh'")
        return
    for index, entry in enumerate(entries):
        if not isinstance(entry, dict) or "frameHex" not in entry:
            checker.fail(path, f"contoh[{index}] tidak memiliki 'frameHex'")
            continue
        label = entry.get("nama", f"contoh[{index}]")
        declared = entry.get("declaredLength")
        checker.frame(
            path,
            label,
            str(entry["frameHex"]),
            expected_payload=entry.get("payloadHex"),
            expected_ascii=entry.get("payloadAscii"),
            expected_declared=declared if isinstance(declared, int) else None,
        )


def validate_dtc(checker: Checker, path: Path) -> None:
    data = checker.load(path)
    if not isinstance(data, dict):
        return
    request = data.get("request")
    if isinstance(request, dict) and "hex" in request:
        checker.frame(path, "request", str(request["hex"]))
    entries = data.get("contohRespons")
    if not isinstance(entries, list):
        checker.fail(path, "tidak memiliki array 'contohRespons'")
        return
    for index, entry in enumerate(entries):
        if not isinstance(entry, dict) or "frameHex" not in entry:
            checker.fail(path, f"contohRespons[{index}] tidak memiliki 'frameHex'")
            continue
        label = entry.get("nama", f"contohRespons[{index}]")
        checker.frame(path, label, str(entry["frameHex"]))


def validate_live_data(checker: Checker, path: Path) -> None:
    data = checker.load(path)
    if not isinstance(data, dict):
        return
    validated_tables = 0
    for table, entry in data.items():
        if not isinstance(entry, dict):
            continue
        if "responseHex" not in entry:
            continue
        validated_tables += 1
        checker.frame(
            path,
            f"{table}.response",
            str(entry["responseHex"]),
            expected_payload=entry.get("payloadHex"),
            payload_start=4,
        )
        if "requestHex" in entry:
            checker.frame(path, f"{table}.request", str(entry["requestHex"]))
    if validated_tables == 0:
        checker.fail(path, "tidak ada tabel dengan 'responseHex'")


def validate_catalog_assets(checker: Checker, path: Path) -> None:
    data = checker.load(path)
    records: Any
    if isinstance(data, list):
        records = data
    elif isinstance(data, dict):
        records = data.get("records")
    else:
        checker.fail(path, "katalog bukan array atau object")
        return
    if not isinstance(records, list):
        # The asset schema is allowed to change; only report when a records
        # array exists but has an unexpected shape.
        return
    for index, record in enumerate(records):
        if not isinstance(record, dict):
            checker.fail(path, f"records[{index}] bukan object")
            continue
        identifier = str(record.get("id", ""))
        if not identifier.startswith("SAMPLE-"):
            checker.fail(path, f"records[{index}] bukan record sintetis SAMPLE-: '{identifier}'")


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Validasi fixture frame RevCore")
    parser.add_argument(
        "--verbose",
        action="store_true",
        help="tampilkan setiap frame yang diperiksa",
    )
    parser.add_argument(
        "--root",
        type=Path,
        default=None,
        help="root repository (default: direktori induk tools/)",
    )
    args = parser.parse_args(argv)

    root = args.root or Path(__file__).resolve().parents[1]
    if not root.is_dir():
        print(f"Root tidak ditemukan: {root}", file=sys.stderr)
        return 2

    checker = Checker(verbose=args.verbose)
    validators = {
        root / "examples" / "protocol_frames" / "requests.json": validate_requests,
        root / "examples" / "ecu_responses" / "identification.json": validate_identification,
        root / "examples" / "dtc" / "dtc_examples.json": validate_dtc,
        root / "examples" / "sensor_data" / "live_data.json": validate_live_data,
        root / "core" / "src" / "main" / "assets" / "ecm_database.json": validate_catalog_assets,
    }

    for path, validator in validators.items():
        if not path.exists():
            checker.fail(path, "berkas tidak ditemukan")
            continue
        print(f"memeriksa {path.relative_to(root)}")
        validator(checker, path)

    print()
    if checker.failures:
        print(f"GAGAL: {len(checker.failures)} masalah pada {checker.checked_frames} frame")
        for failure in checker.failures:
            print(f"  - {failure}")
        return 1

    print(f"LULUS: {checker.checked_frames} frame valid, payload konsisten")
    print("Catatan: ini hanya validasi fixture, bukan bukti kompatibilitas ECU.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
