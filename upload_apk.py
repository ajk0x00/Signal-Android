#!/usr/bin/env python3
"""Upload a built APK to the configured GCS bucket using a service account JSON."""

import argparse
import json
import os
import sys
from pathlib import Path

from google.cloud import storage
from google.oauth2 import service_account

APK_ROOT = Path("/home/deepunabhi/Signal-Android/app/build/outputs/apk")
DEFAULT_SA_JSON = os.path.expanduser("~/signal-extended.json")


def find_apks(root: Path) -> list[Path]:
    return sorted(root.rglob("*.apk"))


def choose_apk(apks: list[Path]) -> Path | None:
    if not apks:
        print(f"No APKs found under {APK_ROOT}", file=sys.stderr)
        return None

    print("Available APKs:")
    for i, apk in enumerate(apks, 1):
        rel = apk.relative_to(APK_ROOT)
        size_mb = apk.stat().st_size / (1024 * 1024)
        print(f"  [{i:>2}] {rel}  ({size_mb:.1f} MiB)")

    while True:
        try:
            raw = input(f"Select APK [1-{len(apks)}] (q to quit): ").strip()
        except EOFError:
            return None
        if raw.lower() in {"q", "quit", "exit"}:
            return None
        if raw.isdigit():
            idx = int(raw)
            if 1 <= idx <= len(apks):
                return apks[idx - 1]
        print(f"Invalid selection: {raw!r}")


def upload(apk_path: Path, sa_json_path: Path, object_name: str | None) -> str:
    with sa_json_path.open() as f:
        sa = json.load(f)

    bucket_name = sa.get("bucket_name") or sa.get("bucket")
    if not bucket_name:
        raise SystemExit(
            f"`bucket_name` missing from {sa_json_path}. "
            f'Please add `"bucket_name": "<your-gcs-bucket>"` to your service account JSON file.'
        )

    credentials = service_account.Credentials.from_service_account_info(sa)
    client = storage.Client(project=sa["project_id"], credentials=credentials)
    bucket = client.bucket(bucket_name)

    blob_name = object_name or apk_path.name
    blob = bucket.blob(blob_name)

    size_mb = apk_path.stat().st_size / (1024 * 1024)
    print(f"Uploading {apk_path} ({size_mb:.1f} MiB) -> gs://{bucket_name}/{blob_name}")
    blob.upload_from_filename(str(apk_path), content_type="application/vnd.android.package-archive")

    return f"gs://{bucket_name}/{blob_name}"


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apk", type=Path, help="Path to a specific APK (skips the interactive picker)")
    parser.add_argument("--apk-root", type=Path, default=APK_ROOT, help="Root directory to scan for APKs")
    parser.add_argument("--sa-json", type=Path, default=Path(DEFAULT_SA_JSON), help="Service account JSON path")
    parser.add_argument("--object-name", help="Destination object name in the bucket (defaults to APK filename)")
    args = parser.parse_args()

    if not args.sa_json.is_file():
        print(f"Service account JSON not found: {args.sa_json}", file=sys.stderr)
        return 1

    if args.apk:
        apk = args.apk
        if not apk.is_file():
            print(f"APK not found: {apk}", file=sys.stderr)
            return 1
    else:
        if not args.apk_root.is_dir():
            print(f"APK root not found: {args.apk_root}", file=sys.stderr)
            return 1
        apk = choose_apk(find_apks(args.apk_root))
        if apk is None:
            return 1

    uri = upload(apk, args.sa_json, args.object_name)
    print(f"Uploaded: {uri}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
