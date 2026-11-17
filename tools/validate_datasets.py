#!/usr/bin/env python3
"""Validate RevCore synthetic datasets and demonstrate map parsing.

Checks, without third-party dependencies:

* ECM database records and the vehicle template use the documented schema;
* public records use SAMPLE- identifiers;
* offsets parse as numbers and numeric fields stay non-negative;
* MAP template axes are strictly increasing and cell dimensions match;
* every cell is numeric and within the generic 0..100 range;
* a bilinear sample can be computed from the map template.

This tool validates synthetic data only. It does not validate ECU compatibility,
ROM offsets, or any real vehicle.

Exit code 0 = all checks passed, 1 = at least one failure, 2 = usage error.
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path
from typing import Any

ECM_REQUIRED = ("id", "partNumber", "bikeName", "statusNotes", "source")
ECM_OFFSET_FIELDS = (
    "startOffset",
    "checksumOffset",
    "revLimiterOffset",
    "fuelMapOffset",
    "ignitionMapOffset",
    "idleTargetOffset",
)
ECM_NON_NEGATIVE = (
    "flashSizeKb",
    "stockRevLimit",
    "maxSafeRevLimit",
    "recommendedRevLimit",
    "fuelMapRows",
    "fuelMapCols",
    "ignitionMapRows",
    "ignitionMapCols",
    "defaultBaudRate",
)
HEX_ID = re.compile(r"^(?:[0-9A-Fa-f]{10}|[0-9A-Fa-f]{12})$")
MAP_STATUSES = {"REFERENCE", "RECONSTRUCTED", "VERIFIED", "UNVERIFIED"}


class Report:
    def __init__(self, verbose: bool = False) -> None:
        self.verbose = verbose
        self.failures: list[str] = []
        self.checked = 0

    def fail(self, path: Path, message: str) -> None:
        self.failures.append(f"{path.name}: {message}")

    def ok(self, message: str) -> None:
        self.checked += 1
        if self.verbose:
            print(f"  ok  {message}")


def load_json(path: Path, report: Report) -> Any | None:
    try:
        with path.open("r", encoding="utf-8") as handle:
            return json.load(handle)
    except (OSError, UnicodeDecodeError, json.JSONDecodeError) as exc:
        report.fail(path, f"JSON tidak dapat dibaca: {exc}")
        return None


def parse_offset(value: Any) -> int | None:
    if isinstance(value, bool):
        return None
    if isinstance(value, int):
        return value
    if isinstance(value, float) and value.is_integer():
        return int(value)
    if isinstance(value, str):
        text = value.strip()
        if not text:
            return None
        try:
            return int(text, 0)
        except ValueError:
            return None
    return None


def ecm_records(data: Any) -> list[Any] | None:
    if isinstance(data, list):
        return data
    if isinstance(data, dict):
        records = data.get("records")
        if isinstance(records, list):
            return records
    return None


def validate_ecm_record(record: Any, index: int, path: Path, report: Report) -> None:
    if not isinstance(record, dict):
        report.fail(path, f"records[{index}] bukan object")
        return

    label = str(record.get("id", f"records[{index}]"))
    for field in ECM_REQUIRED:
        value = record.get(field)
        if not isinstance(value, str) or not value.strip():
            report.fail(path, f"{label}: field wajib '{field}' kosong atau bukan string")

    identifier = str(record.get("id", ""))
    if not identifier.startswith("SAMPLE-"):
        report.fail(path, f"{label}: id publik harus berawalan SAMPLE-")

    ecm_id = str(record.get("ecmId", ""))
    if ecm_id and not HEX_ID.match(ecm_id):
        report.fail(path, f"{label}: ecmId '{ecm_id}' bukan 10/12 digit hex")

    for field in ECM_OFFSET_FIELDS:
        if field not in record:
            continue
        if parse_offset(record[field]) is None:
            report.fail(path, f"{label}: offset '{field}' tidak dapat diparse: {record[field]!r}")

    for field in ECM_NON_NEGATIVE:
        if field not in record:
            continue
        value = record[field]
        if not isinstance(value, int) or isinstance(value, bool):
            report.fail(path, f"{label}: '{field}' harus integer")
        elif value < 0:
            report.fail(path, f"{label}: '{field}' negatif ({value})")

    displacement = record.get("displacementCc", 0.0)
    if not isinstance(displacement, (int, float)) or isinstance(displacement, bool):
        report.fail(path, f"{label}: displacementCc harus angka")
    elif displacement < 0 or displacement > 2000:
        report.fail(path, f"{label}: displacementCc di luar 0..2000 ({displacement})")

    for flag in ("isVerifiedDiagnostic", "isVerifiedRemap"):
        if flag in record and not isinstance(record[flag], bool):
            report.fail(path, f"{label}: '{flag}' harus boolean")

    if record.get("isVerifiedDiagnostic") or record.get("isVerifiedRemap"):
        notes = str(record.get("statusNotes", ""))
        if not notes.strip():
            report.fail(path, f"{label}: verifikasi dinyatakan tanpa statusNotes")

    report.ok(f"{label}: record ECM valid")


def validate_ecm_file(path: Path, report: Report) -> None:
    data = load_json(path, report)
    if data is None:
        return
    records = ecm_records(data)
    if records is None:
        report.fail(path, "harus berupa array atau object dengan array 'records'")
        return
    if not records:
        report.fail(path, "tidak berisi record")
        return
    for index, record in enumerate(records):
        validate_ecm_record(record, index, path, report)


def numeric_axis(axis: Any) -> list[float] | None:
    if not isinstance(axis, list) or len(axis) < 2:
        return None
    values: list[float] = []
    for item in axis:
        if isinstance(item, bool) or not isinstance(item, (int, float)):
            return None
        values.append(float(item))
    return values


def validate_map(path: Path, report: Report) -> dict[str, Any] | None:
    data = load_json(path, report)
    if not isinstance(data, dict):
        if data is not None:
            report.fail(path, "map harus berupa object")
        return None

    if data.get("format") != "revcore-synthetic-map":
        report.fail(path, f"format harus 'revcore-synthetic-map', bukan {data.get('format')!r}")
    if data.get("version") != 1:
        report.fail(path, "version harus 1")

    identifier = str(data.get("id", ""))
    if not identifier.startswith("SAMPLE-"):
        report.fail(path, f"id publik harus berawalan SAMPLE-, bukan '{identifier}'")

    status = data.get("status")
    if status not in MAP_STATUSES:
        report.fail(path, f"status '{status}' tidak termasuk {sorted(MAP_STATUSES)}")

    units = data.get("units")
    if not isinstance(units, dict):
        report.fail(path, "units harus object")
    else:
        for key in ("rpm", "load", "value"):
            if not isinstance(units.get(key), str) or not units[key].strip():
                report.fail(path, f"units.{key} harus string tidak kosong")

    notes = data.get("notes")
    if not isinstance(notes, str) or not notes.strip():
        report.fail(path, "notes wajib menjelaskan asal data")

    axes = data.get("axes")
    rpm_axis = numeric_axis(axes.get("rpm") if isinstance(axes, dict) else None)
    load_axis = numeric_axis(axes.get("load") if isinstance(axes, dict) else None)
    if rpm_axis is None:
        report.fail(path, "axes.rpm harus array angka minimal 2 elemen")
    if load_axis is None:
        report.fail(path, "axes.load harus array angka minimal 2 elemen")
    if rpm_axis is not None:
        for first, second in zip(rpm_axis, rpm_axis[1:]):
            if second <= first:
                report.fail(path, f"axes.rpm tidak menaik ketat: {first} -> {second}")
                break
    if load_axis is not None:
        for first, second in zip(load_axis, load_axis[1:]):
            if second <= first:
                report.fail(path, f"axes.load tidak menaik ketat: {first} -> {second}")
                break

    cells = data.get("cells")
    if not isinstance(cells, list):
        report.fail(path, "cells harus array of array")
        return data
    if rpm_axis is not None and len(cells) != len(rpm_axis):
        report.fail(path, f"jumlah baris {len(cells)} != jumlah RPM {len(rpm_axis)}")
    for row_index, row in enumerate(cells):
        if not isinstance(row, list):
            report.fail(path, f"cells[{row_index}] bukan array")
            continue
        if load_axis is not None and len(row) != len(load_axis):
            report.fail(
                path,
                f"cells[{row_index}] berisi {len(row)} kolom, seharusnya {len(load_axis)}",
            )
        for column_index, value in enumerate(row):
            if value is None:
                report.fail(path, f"cells[{row_index}][{column_index}] null")
            elif isinstance(value, bool) or not isinstance(value, (int, float)):
                report.fail(path, f"cells[{row_index}][{column_index}] bukan angka")
            elif value < 0 or value > 100:
                report.fail(
                    path,
                    f"cells[{row_index}][{column_index}] di luar 0..100 ({value})",
                )

    report.ok(f"{identifier}: struktur map valid ({len(cells)} x {len(cells[0]) if cells else 0})")
    return data


def bracket(axis: list[float], target: float) -> tuple[int, int, float]:
    """Return lower index, upper index and fraction for target inside the axis."""
    if target <= axis[0]:
        return 0, 0, 0.0
    if target >= axis[-1]:
        return len(axis) - 1, len(axis) - 1, 0.0
    for index in range(len(axis) - 1):
        low, high = axis[index], axis[index + 1]
        if low <= target <= high:
            fraction = 0.0 if high == low else (target - low) / (high - low)
            return index, index + 1, fraction
    return len(axis) - 1, len(axis) - 1, 0.0


def sample_bilinear(map_data: dict[str, Any], rpm: float, load: float) -> float:
    rpm_axis = [float(value) for value in map_data["axes"]["rpm"]]
    load_axis = [float(value) for value in map_data["axes"]["load"]]
    cells = map_data["cells"]

    rpm_low, rpm_high, rpm_fraction = bracket(rpm_axis, rpm)
    load_low, load_high, load_fraction = bracket(load_axis, load)

    v00 = float(cells[rpm_low][load_low])
    v01 = float(cells[rpm_low][load_high])
    v10 = float(cells[rpm_high][load_low])
    v11 = float(cells[rpm_high][load_high])

    top = v00 + (v01 - v00) * load_fraction
    bottom = v10 + (v11 - v10) * load_fraction
    return top + (bottom - top) * rpm_fraction


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Validasi dataset sintetis RevCore")
    parser.add_argument("--verbose", action="store_true", help="tampilkan setiap record")
    parser.add_argument("--root", type=Path, default=None, help="root repository")
    parser.add_argument(
        "--demo",
        action="store_true",
        help="tampilkan contoh pembacaan bilinear dari map template",
    )
    args = parser.parse_args(argv)

    root = args.root or Path(__file__).resolve().parents[1]
    if not root.is_dir():
        print(f"Root tidak ditemukan: {root}", file=sys.stderr)
        return 2

    report = Report(verbose=args.verbose)
    targets = (
        (root / "core" / "src" / "main" / "assets" / "ecm_database.json", "ecm"),
        (root / "examples" / "vehicle_db" / "vehicle_template.json", "ecm"),
        (root / "examples" / "maps" / "synthetic_map.json", "map"),
    )

    map_data: dict[str, Any] | None = None
    for path, kind in targets:
        if not path.exists():
            report.fail(path, "berkas tidak ditemukan")
            continue
        print(f"memeriksa {path.relative_to(root)}")
        if kind == "ecm":
            validate_ecm_file(path, report)
        else:
            result = validate_map(path, report)
            if result is not None:
                map_data = result

    if args.demo and map_data is not None:
        rpm, load = 3500.0, 45.0
        value = sample_bilinear(map_data, rpm, load)
        print(f"demo bilinear: rpm={rpm:g}, load={load:g}% -> {value:.2f}")

    print()
    if report.failures:
        print(f"GAGAL: {len(report.failures)} masalah pada {report.checked} pemeriksaan")
        for failure in report.failures:
            print(f"  - {failure}")
        return 1

    print(f"LULUS: {report.checked} pemeriksaan dataset sintetis")
    print("Catatan: ini validasi skema, bukan bukti kompatibilitas ECU.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
