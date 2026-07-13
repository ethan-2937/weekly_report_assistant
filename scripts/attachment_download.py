from __future__ import annotations

import hashlib
import ipaddress
import json
import re
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Callable
from urllib import error, parse, request


DOWNLOAD_INFO_BASE = "https://api.dingtalk.com/v1.0/drive/spaces"
ALLOWED_EXTENSIONS = {".csv", ".docx", ".md", ".pdf", ".pptx", ".txt", ".xls", ".xlsx"}
MAX_FILE_BYTES = 20 * 1024 * 1024
MAX_TOTAL_BYTES = 100 * 1024 * 1024
MAX_JSON_BYTES = 1024 * 1024


class AttachmentDownloadError(RuntimeError):
    pass


@dataclass(frozen=True)
class AttachmentMetadata:
    file_id: str
    space_id: str
    file_name: str
    file_type: str = ""
    file_size: int = 0


@dataclass(frozen=True)
class AttachmentMetadataResult:
    files: tuple[AttachmentMetadata, ...]
    metadata_error: bool = False


@dataclass(frozen=True)
class DownloadedAttachment:
    file_name: str
    local_path: str = ""
    status: str = ""


JsonLoader = Callable[[request.Request], dict[str, Any]]
FileLoader = Callable[[request.Request, Path, int], int]


def _text(value: Any) -> str:
    return str(value or "").strip()


def _size(value: Any) -> int:
    try:
        return max(0, int(value or 0))
    except (TypeError, ValueError):
        return 0


def extract_attachment_metadata(report: dict[str, Any]) -> AttachmentMetadataResult:
    files: list[AttachmentMetadata] = []
    metadata_error = False
    for item in report.get("contents") or []:
        if not isinstance(item, dict) or _text(item.get("key")) != "附件":
            continue
        raw_value = item.get("value")
        if raw_value in (None, "", []):
            continue
        try:
            parsed = json.loads(raw_value) if isinstance(raw_value, str) else raw_value
        except (TypeError, json.JSONDecodeError):
            metadata_error = True
            continue
        if not isinstance(parsed, list):
            metadata_error = True
            continue
        for file_item in parsed:
            if not isinstance(file_item, dict):
                metadata_error = True
                continue
            files.append(
                AttachmentMetadata(
                    file_id=_text(file_item.get("fileId")),
                    space_id=_text(file_item.get("spaceId")),
                    file_name=_text(file_item.get("fileName")) or "未命名附件",
                    file_type=_text(file_item.get("fileType")),
                    file_size=_size(file_item.get("fileSize")),
                )
            )
    return AttachmentMetadataResult(tuple(files), metadata_error)


def _safe_filename(value: str) -> str:
    name = Path(value).name.strip() or "attachment"
    name = re.sub(r"[<>:\"/\\|?*\x00-\x1f]", "_", name)
    if len(name) > 120:
        suffix = Path(name).suffix[:16]
        name = name[: 120 - len(suffix)] + suffix
    return name


def _safe_https_url(value: str) -> str:
    parsed = parse.urlparse(value)
    if parsed.scheme != "https" or not parsed.hostname:
        raise AttachmentDownloadError("attachment URL must use HTTPS")
    hostname = parsed.hostname.lower()
    if hostname == "localhost" or hostname.endswith(".localhost"):
        raise AttachmentDownloadError("attachment URL host is not allowed")
    try:
        address = ipaddress.ip_address(hostname)
    except ValueError:
        return value
    if not address.is_global:
        raise AttachmentDownloadError("attachment URL host is not allowed")
    return value


class _SafeRedirectHandler(request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        _safe_https_url(newurl)
        return super().redirect_request(req, fp, code, msg, headers, newurl)


def _load_json(req: request.Request) -> dict[str, Any]:
    try:
        with request.urlopen(req, timeout=30) as response:
            payload = response.read(MAX_JSON_BYTES + 1)
    except (error.HTTPError, error.URLError, TimeoutError) as exc:
        raise AttachmentDownloadError("download information request failed") from exc
    if len(payload) > MAX_JSON_BYTES:
        raise AttachmentDownloadError("download information response is too large")
    try:
        parsed = json.loads(payload.decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError) as exc:
        raise AttachmentDownloadError("download information response is invalid") from exc
    if not isinstance(parsed, dict):
        raise AttachmentDownloadError("download information response is invalid")
    return parsed


def _load_file(req: request.Request, target: Path, max_bytes: int) -> int:
    opener = request.build_opener(_SafeRedirectHandler())
    part = target.with_suffix(target.suffix + ".part")
    total = 0
    try:
        with opener.open(req, timeout=60) as response, part.open("wb") as output:
            content_length = _size(response.headers.get("Content-Length"))
            if content_length > max_bytes:
                raise AttachmentDownloadError("attachment exceeds the size limit")
            while True:
                chunk = response.read(64 * 1024)
                if not chunk:
                    break
                total += len(chunk)
                if total > max_bytes:
                    raise AttachmentDownloadError("attachment exceeds the size limit")
                output.write(chunk)
        part.replace(target)
    except (error.HTTPError, error.URLError, TimeoutError, OSError) as exc:
        raise AttachmentDownloadError("attachment download failed") from exc
    finally:
        if part.exists():
            part.unlink()
    return total


def _download_info_request(access_token: str, union_id: str, item: AttachmentMetadata) -> request.Request:
    space_id = parse.quote(item.space_id, safe="")
    file_id = parse.quote(item.file_id, safe="")
    query = parse.urlencode({"unionId": union_id})
    return request.Request(
        f"{DOWNLOAD_INFO_BASE}/{space_id}/files/{file_id}/downloadInfos?{query}",
        headers={"x-acs-dingtalk-access-token": access_token},
        method="GET",
    )


def _resource_request(payload: dict[str, Any]) -> request.Request:
    download_info = payload.get("downloadInfo")
    if not isinstance(download_info, dict):
        raise AttachmentDownloadError("download information is missing")
    resource_url = _safe_https_url(_text(download_info.get("resourceUrl")))
    headers = download_info.get("headers")
    safe_headers = {
        _text(key): _text(value)
        for key, value in (headers.items() if isinstance(headers, dict) else [])
        if _text(key) and _text(value)
    }
    return request.Request(resource_url, headers=safe_headers, method="GET")


def download_team_lead_attachments(
    access_token: str,
    users: list[dict[str, Any]],
    reports: list[dict[str, Any]],
    week_out: Path,
    json_loader: JsonLoader = _load_json,
    file_loader: FileLoader = _load_file,
) -> dict[str, tuple[DownloadedAttachment, ...]]:
    reports_by_user = {
        _text(report.get("creator_id")): report
        for report in reports
        if _text(report.get("creator_id"))
    }
    results: dict[str, tuple[DownloadedAttachment, ...]] = {}
    total_downloaded = 0
    for user in users:
        if user.get("leader") is not True:
            continue
        userid = _text(user.get("userid"))
        report = reports_by_user.get(userid)
        if not report:
            continue
        metadata = extract_attachment_metadata(report)
        if not metadata.files:
            continue
        union_id = _text(user.get("unionid"))
        user_dir = week_out / "attachments" / "team_leads" / hashlib.sha256(userid.encode("utf-8")).hexdigest()[:12]
        downloads: list[DownloadedAttachment] = []
        for index, item in enumerate(metadata.files, 1):
            safe_name = _safe_filename(item.file_name)
            if not union_id:
                downloads.append(DownloadedAttachment(safe_name, status="缺少 unionId，未下载"))
                continue
            if not item.file_id or not item.space_id:
                downloads.append(DownloadedAttachment(safe_name, status="附件标识不完整，未下载"))
                continue
            if Path(safe_name).suffix.lower() not in ALLOWED_EXTENSIONS:
                downloads.append(DownloadedAttachment(safe_name, status="不支持的附件类型"))
                continue
            if item.file_size > MAX_FILE_BYTES:
                downloads.append(DownloadedAttachment(safe_name, status="附件超过大小限制"))
                continue
            remaining = MAX_TOTAL_BYTES - total_downloaded
            if remaining <= 0:
                downloads.append(DownloadedAttachment(safe_name, status="本周附件总量超过限制"))
                continue
            try:
                info = json_loader(_download_info_request(access_token, union_id, item))
            except (AttachmentDownloadError, OSError, ValueError):
                downloads.append(DownloadedAttachment(safe_name, status="附件下载信息获取失败"))
                continue
            try:
                resource_request = _resource_request(info)
                user_dir.mkdir(parents=True, exist_ok=True)
                target = user_dir / f"{index:02d}_{safe_name}"
                size = file_loader(resource_request, target, min(MAX_FILE_BYTES, remaining))
                total_downloaded += size
                relative_path = target.relative_to(week_out).as_posix()
                downloads.append(DownloadedAttachment(safe_name, relative_path, "已下载"))
            except (AttachmentDownloadError, OSError, ValueError):
                downloads.append(DownloadedAttachment(safe_name, status="附件下载失败"))
        results[userid] = tuple(downloads)
    return results
