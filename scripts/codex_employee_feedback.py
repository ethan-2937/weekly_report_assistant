from __future__ import annotations

import json
import re
from pathlib import Path
from typing import Any, Protocol


SENSITIVE_FEEDBACK = (
    re.compile(r"(?i)access[_ -]?token\s*[:=]\s*\S+"),
    re.compile(r"(?i)(?:appsecret|client[_ -]?secret|password|密码)\s*[:=：]\s*\S+"),
    re.compile(r"(?i)bearer\s+[A-Za-z0-9._~+/=-]{12,}"),
    re.compile(r"eyJ[A-Za-z0-9_-]{20,}\.[A-Za-z0-9_-]{10,}"),
)
FORBIDDEN_FEEDBACK = (
    re.compile(r"(?i)https?://"),
    re.compile(r"(?i)\b(userid|unionid|fileid|spaceid)\b"),
    re.compile(r"(?i)(?:[A-Z]:\\|/app/|output/)"),
)
THANKS_MIN_LENGTH = 24
THANKS_MAX_LENGTH = 220


class RosterLike(Protocol):
    expected_names: tuple[str, ...]
    userids: tuple[str, ...]
    submitted_userids: tuple[str, ...]


def validate_employee_feedback(
    feedback: Any,
    roster: RosterLike,
    require_thanks: bool = True,
) -> tuple[str, ...]:
    if not isinstance(feedback, (list, tuple)) or len(feedback) > 300:
        return ("EMPLOYEE_FEEDBACK_SCHEMA_INVALID",)
    errors: list[str] = []
    seen: set[str] = set()
    for item in feedback:
        expected_fields = {"userid", "praise", "improvement", "thanks"} if require_thanks else {
            "userid", "praise", "improvement"
        }
        if not isinstance(item, dict) or set(item) != expected_fields:
            errors.append("EMPLOYEE_FEEDBACK_SCHEMA_INVALID")
            continue
        userid = item.get("userid")
        praise = item.get("praise")
        improvement = item.get("improvement")
        thanks = item.get("thanks", "")
        if not all(isinstance(value, str) for value in (userid, praise, improvement, thanks)):
            errors.append("EMPLOYEE_FEEDBACK_SCHEMA_INVALID")
            continue
        if not userid or len(userid) > 128 or userid in seen:
            errors.append("EMPLOYEE_FEEDBACK_IDENTITY_INVALID")
        seen.add(userid)
        if not 4 <= len(praise.strip()) <= 400 or not 4 <= len(improvement.strip()) <= 700:
            errors.append("EMPLOYEE_FEEDBACK_CONTENT_INVALID")
        if require_thanks and (
            not THANKS_MIN_LENGTH <= len(thanks.strip()) <= THANKS_MAX_LENGTH
            or not thanks.strip().startswith("感谢您")
            or "团队因您" not in thanks
        ):
            errors.append("EMPLOYEE_FEEDBACK_THANKS_INVALID")
        prose = f"{praise}\n{improvement}\n{thanks}"
        if any(pattern.search(prose) for pattern in SENSITIVE_FEEDBACK):
            errors.append("EMPLOYEE_FEEDBACK_SECRET_EXPOSED")
        if any(pattern.search(prose) for pattern in FORBIDDEN_FEEDBACK):
            errors.append("EMPLOYEE_FEEDBACK_FORBIDDEN_CONTENT")
        if any(stable_id in prose for stable_id in roster.userids if len(stable_id) >= 6):
            errors.append("EMPLOYEE_FEEDBACK_USERID_EXPOSED")
        if any(name in prose for name in roster.expected_names if name):
            errors.append("EMPLOYEE_FEEDBACK_NAME_EXPOSED")
    if seen != set(roster.submitted_userids):
        errors.append("EMPLOYEE_FEEDBACK_COVERAGE_INVALID")
    return tuple(dict.fromkeys(errors))


def validate_employee_feedback_artifact(
    path: Path,
    week_label: str,
    roster: RosterLike,
    input_digest: str,
    report_digest: str,
) -> tuple[str, ...]:
    if not path.is_file():
        return ("EMPLOYEE_FEEDBACK_ARTIFACT_MISSING",)
    try:
        if path.stat().st_size > 512 * 1024:
            return ("EMPLOYEE_FEEDBACK_ARTIFACT_TOO_LARGE",)
        payload = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, UnicodeError, json.JSONDecodeError):
        return ("EMPLOYEE_FEEDBACK_ARTIFACT_INVALID",)
    if not isinstance(payload, dict):
        return ("EMPLOYEE_FEEDBACK_ARTIFACT_INVALID",)
    if (
        payload.get("version") not in (1, 2)
        or payload.get("weekLabel") != week_label
        or payload.get("inputDigest") != input_digest
        or payload.get("reportDigest") != report_digest
    ):
        return ("EMPLOYEE_FEEDBACK_ARTIFACT_MISMATCH",)
    return validate_employee_feedback(
        payload.get("feedback"),
        roster,
        require_thanks=payload.get("version") == 2,
    )
