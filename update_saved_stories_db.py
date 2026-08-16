#!/usr/bin/env python3
"""Rebuild a profile's saved_stories_db.json from the objects actually present in the GCS bucket.

The Signal app stores, per profile, under the bucket:
  <profileName>/saved_stories_db.json   - the index the app reads
  <profileName>/<fileName>              - the saved story objects (image / video / rendered text)
  <profileName>/thumbnails/<base>.jpg   - generated video thumbnails

This script lists a profile's objects and reconciles the index to match: it adds records for
objects with no entry, drops entries whose object is gone, links video thumbnails, and (by default)
merges with the existing index so metadata the bucket can't represent is preserved -- specifically
TEXT-vs-IMAGE media type (text stories are uploaded as .jpg) and the original send timestamp.
"""

import argparse
import json
import os
import sys
from datetime import datetime, timezone
from pathlib import Path

from google.cloud import storage
from google.oauth2 import service_account

DEFAULT_SA_JSON = os.path.expanduser("~/signal-extended.json")
DB_OBJECT_NAME = "saved_stories_db.json"
THUMBNAILS_SEGMENT = "thumbnails/"
DB_VERSION = 1

VIDEO_EXTS = {"mp4", "mov", "m4v", "3gp", "mkv", "webm", "avi"}
IMAGE_EXTS = {"jpg", "jpeg", "png", "gif", "webp", "heic", "heif", "bmp"}

# Matches SaveStoryToCloudJob's SimpleDateFormat("dd-MM-yyyy_HH-mm-ss").
FILENAME_TS_FORMAT = "%d-%m-%Y_%H-%M-%S"
FILENAME_TS_LEN = len("dd-MM-yyyy_HH-mm-ss")


def load_sa(sa_json_path: Path) -> dict:
    with sa_json_path.open() as f:
        sa = json.load(f)
    bucket_name = sa.get("bucket_name") or sa.get("bucket")
    if not bucket_name:
        raise SystemExit(
            f"`bucket_name` missing from {sa_json_path}. "
            f'Please add `"bucket_name": "<your-gcs-bucket>"` to your service account JSON file.'
        )
    sa["bucket_name"] = bucket_name
    return sa


def make_bucket(sa: dict):
    credentials = service_account.Credentials.from_service_account_info(sa)
    client = storage.Client(project=sa["project_id"], credentials=credentials)
    return client.bucket(sa["bucket_name"])


def list_top_level_prefixes(bucket) -> list[str]:
    it = bucket.list_blobs(delimiter="/")
    # Consume the iterator so .prefixes is populated.
    for _ in it:
        pass
    return sorted(p.rstrip("/") for p in it.prefixes)


def choose_profile(bucket) -> str | None:
    profiles = list_top_level_prefixes(bucket)
    if not profiles:
        print("No profile folders found in bucket.", file=sys.stderr)
        return None
    print("Available profiles:")
    for i, name in enumerate(profiles, 1):
        print(f"  [{i:>2}] {name}")
    while True:
        try:
            raw = input(f"Select profile [1-{len(profiles)}] (q to quit): ").strip()
        except EOFError:
            return None
        if raw.lower() in {"q", "quit", "exit"}:
            return None
        if raw.isdigit() and 1 <= int(raw) <= len(profiles):
            return profiles[int(raw) - 1]
        print(f"Invalid selection: {raw!r}")


def ext_of(name: str) -> str:
    base = name.rsplit("/", 1)[-1]
    return base.rsplit(".", 1)[-1].lower() if "." in base else ""


def infer_media_type(file_name: str, content_type: str | None) -> str:
    if content_type:
        if content_type.startswith("video/"):
            return "VIDEO"
        if content_type.startswith("image/"):
            return "IMAGE"
    ext = ext_of(file_name)
    if ext in VIDEO_EXTS:
        return "VIDEO"
    return "IMAGE"


def timestamp_from_filename(file_name: str) -> int | None:
    """Parse the leading dd-MM-yyyy_HH-mm-ss that SaveStoryToCloudJob embeds, as epoch millis."""
    stem = file_name.rsplit("/", 1)[-1]
    if len(stem) < FILENAME_TS_LEN:
        return None
    try:
        dt = datetime.strptime(stem[:FILENAME_TS_LEN], FILENAME_TS_FORMAT)
    except ValueError:
        return None
    # Filenames carry no timezone; interpret as local time to match how the app formatted dateSent.
    return int(dt.astimezone().timestamp() * 1000)


def base_name(file_name: str) -> str:
    """File name without its extension, e.g. '30-05-2026_10-44-05_12.mp4' -> '30-05-2026_10-44-05_12'."""
    return file_name.rsplit(".", 1)[0] if "." in file_name else file_name


def reconcile(bucket, profile: str, keep_missing: bool) -> tuple[dict, dict]:
    prefix = f"{profile}/"
    thumb_prefix = f"{prefix}{THUMBNAILS_SEGMENT}"
    db_object = f"{prefix}{DB_OBJECT_NAME}"

    existing_by_object: dict[str, dict] = {}
    existing_db: dict = {}
    db_blob = bucket.get_blob(db_object)
    if db_blob is not None:
        try:
            existing_db = json.loads(db_blob.download_as_bytes().decode("utf-8"))
            for rec in existing_db.get("savedStories", []):
                if rec.get("objectName"):
                    existing_by_object[rec["objectName"]] = rec
        except (json.JSONDecodeError, UnicodeDecodeError) as e:
            print(f"  ! Existing {DB_OBJECT_NAME} is unreadable, rebuilding from scratch: {e}", file=sys.stderr)

    story_blobs: list = []
    thumb_by_base: dict[str, str] = {}
    for blob in bucket.list_blobs(prefix=prefix):
        name = blob.name
        if name == db_object or name.endswith("/") or name == prefix:
            continue
        if name.startswith(thumb_prefix):
            thumb_file = name[len(thumb_prefix):]
            thumb_by_base[base_name(thumb_file)] = name
            continue
        story_blobs.append(blob)

    records: list[dict] = []
    added, kept = [], []
    for blob in sorted(story_blobs, key=lambda b: b.name):
        object_name = blob.name
        file_name = object_name[len(prefix):]
        prior = existing_by_object.get(object_name)

        media_type = (prior.get("mediaType") if prior else None) or infer_media_type(file_name, blob.content_type)
        timestamp = (
            prior.get("timestamp")
            if prior and prior.get("timestamp")
            else (timestamp_from_filename(file_name) or int((blob.updated or blob.time_created).timestamp() * 1000))
        )
        thumbnail = thumb_by_base.get(base_name(file_name))
        if thumbnail is None and prior:
            thumbnail = prior.get("thumbnailObjectName")

        records.append({
            "fileName": file_name,
            "mediaType": media_type,
            "timestamp": timestamp,
            "fileSize": int(blob.size or 0),
            "senderName": (prior.get("senderName") if prior else profile) or profile,
            "objectName": object_name,
            "thumbnailObjectName": thumbnail,
        })
        (kept if prior else added).append(object_name)

    present = {b.name for b in story_blobs}
    dropped = [obj for obj in existing_by_object if obj not in present]
    if keep_missing:
        for obj in dropped:
            records.append(existing_by_object[obj])
        dropped = []

    records.sort(key=lambda r: r.get("timestamp", 0), reverse=True)
    now_ms = int(datetime.now(timezone.utc).timestamp() * 1000)
    new_db = {
        "version": existing_db.get("version", DB_VERSION),
        "lastSyncTimestamp": now_ms,
        "savedStories": records,
    }
    summary = {
        "prefix": prefix,
        "db_object": db_object,
        "total": len(records),
        "added": added,
        "kept": kept,
        "dropped": dropped,
        "thumbnails": len(thumb_by_base),
    }
    return new_db, summary


def print_summary(s: dict) -> None:
    print(f"\nProfile prefix : gs://.../{s['prefix']}")
    print(f"Story objects  : {s['total']} ({len(s['added'])} new, {len(s['kept'])} existing)")
    print(f"Thumbnails     : {s['thumbnails']}")
    for obj in s["added"]:
        print(f"  + {obj}")
    for obj in s["dropped"]:
        print(f"  - {obj}  (no longer in bucket, removed from index)")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--sa-json", type=Path, default=Path(DEFAULT_SA_JSON), help="Service account JSON path")
    parser.add_argument("--profile-name", help="Profile folder to reconcile (skips the interactive picker)")
    parser.add_argument("--keep-missing", action="store_true",
                        help="Keep index records whose object is absent from the bucket (default: drop them)")
    parser.add_argument("--dry-run", action="store_true", help="Show what would change without uploading")
    args = parser.parse_args()

    if not args.sa_json.is_file():
        print(f"Service account JSON not found: {args.sa_json}", file=sys.stderr)
        return 1

    sa = load_sa(args.sa_json)
    bucket = make_bucket(sa)
    print(f"Bucket: gs://{sa['bucket_name']}")

    profile = args.profile_name or choose_profile(bucket)
    if not profile:
        return 1

    new_db, summary = reconcile(bucket, profile, args.keep_missing)
    print_summary(summary)

    payload = json.dumps(new_db, separators=(",", ":"))
    if args.dry_run:
        print(f"\n[dry-run] Would upload {summary['db_object']} ({len(payload)} bytes). Not writing.")
        return 0

    bucket.blob(summary["db_object"]).upload_from_string(payload, content_type="application/json")
    print(f"\nUpdated: gs://{sa['bucket_name']}/{summary['db_object']} ({summary['total']} records)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
