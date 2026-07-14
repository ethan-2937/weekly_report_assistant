from __future__ import annotations

import argparse
import json
import os
import re
from datetime import datetime, timedelta, timezone
from pathlib import Path
from typing import Any
from urllib import error, request
from urllib.parse import urlsplit, urlunsplit


ROOT = Path(__file__).resolve().parents[1]
ENV_PATH = ROOT / "config" / ".env"
DEFAULT_OUTPUT_ROOT = ROOT / "output"
CN_TZ = timezone(timedelta(hours=8))


class DingTalkError(RuntimeError):
    pass


def load_env(path: Path = ENV_PATH) -> dict[str, str]:
    """Load a small .env file without requiring third-party packages."""
    values: dict[str, str] = {}
    if path.exists():
        for raw_line in path.read_text(encoding="utf-8").splitlines():
            line = raw_line.strip().lstrip("\ufeff")
            if not line or line.startswith("#") or "=" not in line:
                continue
            key, value = line.split("=", 1)
            values[key.strip().lstrip("\ufeff")] = value.strip().strip('"').strip("'")

    merged = values
    merged.update(os.environ)
    return merged


def require_env(env: dict[str, str], name: str) -> str:
    value = env.get(name, "").strip()
    if not value or value.startswith("请在本机填写"):
        raise DingTalkError(f"Missing required config: {name}. Please set it in {ENV_PATH}")
    return value


def api_post(url: str, payload: dict[str, Any], token: str | None = None) -> dict[str, Any]:
    data = json.dumps(payload, ensure_ascii=False).encode("utf-8")
    headers = {"Content-Type": "application/json"}
    if token:
        headers["x-acs-dingtalk-access-token"] = token
    req = request.Request(url, data=data, headers=headers, method="POST")
    try:
        with request.urlopen(req, timeout=30) as resp:
            body = resp.read().decode("utf-8")
    except error.HTTPError as exc:
        raise DingTalkError(f"HTTP {exc.code} for {_safe_url(url)}") from exc
    except error.URLError as exc:
        raise DingTalkError(f"Network error for {_safe_url(url)}: {_safe_response(str(exc.reason))}") from exc

    try:
        parsed = json.loads(body)
    except json.JSONDecodeError as exc:
        raise DingTalkError(f"Non-JSON response from {_safe_url(url)}") from exc

    errcode = parsed.get("errcode")
    if errcode not in (None, 0):
        raise DingTalkError(f"DingTalk API error {errcode}: {_safe_response(str(parsed.get('errmsg') or 'unknown error'))}")
    return parsed


def _safe_url(url: str) -> str:
    parts = urlsplit(url)
    return urlunsplit((parts.scheme, parts.netloc, parts.path, "", ""))


def _safe_response(value: str) -> str:
    text = value.replace("\r", " ").replace("\n", " ").strip()
    text = re.sub(r"(?i)(access[_-]?token|appsecret|app_secret)([=:]\s*)[^,;&\s]+", r"\1\2***", text)
    text = re.sub(r"(?i)bearer\s+[A-Za-z0-9._~+/=-]+", "Bearer ***", text)
    return text[:160] if text else "request failed"


def get_access_token(env: dict[str, str] | None = None) -> str:
    env = env or load_env()
    app_key = require_env(env, "DINGTALK_APP_KEY")
    app_secret = require_env(env, "DINGTALK_APP_SECRET")
    result = api_post(
        "https://api.dingtalk.com/v1.0/oauth2/accessToken",
        {"appKey": app_key, "appSecret": app_secret},
    )
    token = result.get("accessToken")
    if not token:
        raise DingTalkError(f"accessToken not found in response: {result}")
    return token


def topapi_post(path: str, payload: dict[str, Any], access_token: str) -> dict[str, Any]:
    url = f"https://oapi.dingtalk.com{path}?access_token={access_token}"
    return api_post(url, payload)


def output_root(env: dict[str, str]) -> Path:
    configured = env.get("OUTPUT_ROOT", "").strip()
    if not configured:
        return DEFAULT_OUTPUT_ROOT
    path = Path(configured)
    if not path.is_absolute():
        path = ROOT / path
    return path


def parse_date(value: str) -> datetime:
    return datetime.strptime(value, "%Y-%m-%d").replace(tzinfo=CN_TZ)


def week_range(week: str = "previous", now: datetime | None = None) -> tuple[datetime, datetime]:
    now = (now or datetime.now(CN_TZ)).astimezone(CN_TZ)
    offset = -1 if week == "previous" else 0
    start = (now - timedelta(days=now.weekday()) + timedelta(weeks=offset)).replace(
        hour=0, minute=0, second=0, microsecond=0
    )
    end = start + timedelta(days=7) - timedelta(milliseconds=1)
    return start, end


def submission_window(period_start: datetime, period_end: datetime) -> tuple[datetime, datetime]:
    """Shift a report period to its Thursday-through-Wednesday submission window."""
    grace_shift = timedelta(days=3)
    return period_start + grace_shift, period_end + grace_shift


def add_week_args(parser: argparse.ArgumentParser) -> None:
    parser.add_argument(
        "--week",
        choices=["previous", "current"],
        default="previous",
        help="Report week to process. Submissions are attributed from Thursday through the following Wednesday.",
    )
    parser.add_argument("--start", help="Report period start, format YYYY-MM-DD. Overrides --week.")
    parser.add_argument("--end", help="Report period end, format YYYY-MM-DD. Default: --start + 6 days.")


def resolve_week_args(args: argparse.Namespace) -> tuple[datetime, datetime, str]:
    if args.start:
        start = parse_date(args.start)
        if args.end:
            end = parse_date(args.end) + timedelta(days=1) - timedelta(milliseconds=1)
        else:
            end = start + timedelta(days=7) - timedelta(milliseconds=1)
    else:
        start, end = week_range(getattr(args, "week", "previous"))
    label = f"{start.isocalendar().year}-W{start.isocalendar().week:02d}"
    return start, end, label


def write_json(path: Path, data: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8")
