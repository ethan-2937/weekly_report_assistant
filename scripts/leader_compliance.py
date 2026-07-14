from __future__ import annotations

from dataclasses import dataclass
from typing import Any

from attachment_download import (
    DownloadedAttachment,
    extract_attachment_metadata,
)
from leader_subordinates import map_subordinates
from report_content import report_text


NOT_APPLICABLE = "不适用（个人周报未提交）"
ATTACHMENT_PENDING = "附件待解析"
NO_EVIDENCE = "未见证据"

TEAM_SUMMARY_TERMS = ("团队汇总", "团队周报", "团队总结", "整体进度", "成员评价")
EVIDENCE_TERMS = {
    "overall_progress": ("整体进度", "团队进度", "整体情况"),
    "member_evaluation": ("个人工作评价", "成员评价", "人员评价"),
    "risk": ("风险点", "风险", "阻塞"),
    "resource_need": ("资源需求", "资源协调", "需要支持"),
    "reminder": ("催交", "提醒提交", "提醒周报", "周报提醒"),
}


@dataclass(frozen=True)
class AttachmentEvidence:
    file_names: tuple[str, ...]
    metadata_error: bool = False


@dataclass(frozen=True)
class TeamLeadEvidence:
    name: str
    userid: str
    dept: str
    title: str
    personal_report: str
    team_summary: str
    attachment: str
    overall_progress: str
    member_evaluation: str
    risk: str
    resource_need: str
    reminder: str
    subordinate_names: tuple[str, ...] = ()
    subordinate_userids: tuple[str, ...] = ()
    subordinate_source: str = "无法判断"
    subordinate_missing_names: tuple[str, ...] = ()
    subordinate_status: str = "无法判断"


def _text(value: Any) -> str:
    return str(value or "").strip()


def _dept_names(user: dict[str, Any], dept_by_id: dict[Any, str]) -> str:
    return "/".join(
        _text(dept_by_id.get(dept_id, dept_id))
        for dept_id in user.get("dept_id_list") or []
    )


def extract_attachment_evidence(report: dict[str, Any]) -> AttachmentEvidence:
    metadata = extract_attachment_metadata(report)
    return AttachmentEvidence(
        tuple(item.file_name for item in metadata.files),
        metadata.metadata_error,
    )


def _body_evidence(body: str, terms: tuple[str, ...]) -> bool:
    return any(term in body for term in terms)


def _leadership_body(report: dict[str, Any]) -> str:
    content = report.get("contents")
    if not isinstance(content, list):
        return report_text(report)
    without_attachments = [
        item
        for item in content
        if not isinstance(item, dict) or _text(item.get("key")) != "附件"
    ]
    return report_text({**report, "contents": without_attachments})


def _evidence_status(
    body: str,
    terms: tuple[str, ...],
    attachments: AttachmentEvidence,
    downloads: tuple[DownloadedAttachment, ...],
) -> str:
    if _body_evidence(body, terms):
        return "正文有相关证据"
    if any(item.local_path for item in downloads):
        return "待 Codex 解析"
    if attachments.file_names:
        return ATTACHMENT_PENDING
    if attachments.metadata_error:
        return "无法判断（附件元数据异常）"
    return NO_EVIDENCE


def _team_summary_status(body: str, attachments: AttachmentEvidence) -> str:
    if _body_evidence(body, TEAM_SUMMARY_TERMS):
        return "正文有团队汇总证据"
    if attachments.file_names:
        if any(_body_evidence(name, TEAM_SUMMARY_TERMS) for name in attachments.file_names):
            return "疑似已提交（附件名匹配）"
        return "有附件待确认"
    if attachments.metadata_error:
        return "无法判断（附件元数据异常）"
    return NO_EVIDENCE


def _attachment_status(
    attachments: AttachmentEvidence,
    downloads: tuple[DownloadedAttachment, ...],
) -> str:
    local_paths = [item.local_path for item in downloads if item.local_path]
    if local_paths:
        return "已下载待 Codex 解析：" + "、".join(local_paths)
    if attachments.file_names:
        names = "、".join(attachments.file_names)
        failures = "、".join(item.status for item in downloads if item.status and not item.local_path)
        suffix = f"；{failures}" if failures else ""
        return f"{len(attachments.file_names)} 个附件，正文未解析：{names}{suffix}"
    if attachments.metadata_error:
        return "附件元数据异常"
    return "无附件"


def build_team_lead_evidence(
    users: list[dict[str, Any]],
    reports: list[dict[str, Any]],
    dept_by_id: dict[Any, str],
    attachment_downloads: dict[str, tuple[DownloadedAttachment, ...]] | None = None,
) -> list[TeamLeadEvidence]:
    reports_by_user = {
        _text(report.get("creator_id")): report
        for report in reports
        if _text(report.get("creator_id"))
    }
    evidence: list[TeamLeadEvidence] = []
    attachment_downloads = attachment_downloads or {}
    leaders = [user for user in users if user.get("leader") is True]
    subordinate_by_leader = {
        _text(user.get("userid")): map_subordinates(user, users, reports_by_user)
        for user in leaders
    }
    for user in sorted(leaders, key=lambda item: (_dept_names(item, dept_by_id), _text(item.get("name")))):
        subordinate = subordinate_by_leader.get(_text(user.get("userid")), ((), (), "无法判断", (), "无法判断"))
        report = reports_by_user.get(_text(user.get("userid")))
        if not report:
            evidence.append(
                TeamLeadEvidence(
                    name=_text(user.get("name")),
                    userid=_text(user.get("userid")),
                    dept=_dept_names(user, dept_by_id),
                    title=_text(user.get("title")),
                    personal_report="未提交",
                    team_summary=NOT_APPLICABLE,
                    attachment=NOT_APPLICABLE,
                    overall_progress=NOT_APPLICABLE,
                    member_evaluation=NOT_APPLICABLE,
                    risk=NOT_APPLICABLE,
                    resource_need=NOT_APPLICABLE,
                    reminder=NOT_APPLICABLE,
                    subordinate_names=subordinate[0],
                    subordinate_userids=subordinate[1],
                    subordinate_source=subordinate[2],
                    subordinate_missing_names=subordinate[3],
                    subordinate_status=subordinate[4],
                )
            )
            continue

        body = _leadership_body(report)
        attachments = extract_attachment_evidence(report)
        downloads = attachment_downloads.get(_text(user.get("userid")), ())
        evidence.append(
            TeamLeadEvidence(
                name=_text(user.get("name")),
                userid=_text(user.get("userid")),
                dept=_dept_names(user, dept_by_id),
                title=_text(user.get("title")),
                personal_report="已提交",
                team_summary=_team_summary_status(body, attachments),
                attachment=_attachment_status(attachments, downloads),
                overall_progress=_evidence_status(body, EVIDENCE_TERMS["overall_progress"], attachments, downloads),
                member_evaluation=_evidence_status(body, EVIDENCE_TERMS["member_evaluation"], attachments, downloads),
                risk=_evidence_status(body, EVIDENCE_TERMS["risk"], attachments, downloads),
                resource_need=_evidence_status(body, EVIDENCE_TERMS["resource_need"], attachments, downloads),
                reminder=_evidence_status(body, EVIDENCE_TERMS["reminder"], attachments, downloads),
                subordinate_names=subordinate[0],
                subordinate_userids=subordinate[1],
                subordinate_source=subordinate[2],
                subordinate_missing_names=subordinate[3],
                subordinate_status=subordinate[4],
            )
        )
    return evidence


def _cell(value: str) -> str:
    return value.replace("|", "\\|").replace("\r", " ").replace("\n", " ")


def render_team_lead_input(evidence: list[TeamLeadEvidence]) -> list[str]:
    lines = [
        "## 团队负责人履职输入（确定性证据）",
        "",
        "> 附件待解析不等于未提交；正文证据仍需判断是否覆盖团队范围，不得用个人产出代替团队履职。",
        "",
    ]
    if not evidence:
        return lines + ["- 未识别到团队负责人候选。", ""]
    lines.extend(
        [
            "| 负责人 | userid | 管理团队/部门 | 职务 | 个人周报 | 团队汇总证据 | 附件状态 | 整体进度 | 成员评价 | 风险点 | 资源需求 | 催交提醒 |",
            "|---|---|---|---|---|---|---|---|---|---|---|---|",
        ]
    )
    for item in evidence:
        values = (
            item.name,
            item.userid,
            item.dept,
            item.title,
            item.personal_report,
            item.team_summary,
            item.attachment,
            item.overall_progress,
            item.member_evaluation,
            item.risk,
            item.resource_need,
            item.reminder,
        )
        lines.append("| " + " | ".join(_cell(value) for value in values) + " |")
    lines.append("")
    lines.extend(
        [
            "## 负责人下属映射（待上级确认）",
            "",
            "> 有明确下属 userid 时优先使用；没有时按共享钉钉所属部门生成候选。部门回退只是待确认名单，不代表正式汇报关系。",
            "",
            "| 负责人 | 负责人 userid | 映射来源 | 下属人数 | 下属员工 | 未提交下属 | 履职风险 |",
            "|---|---|---|---:|---|---|---|",
        ]
    )
    for item in evidence:
        lines.append(
            "| "
            + " | ".join(
                _cell(value)
                for value in (
                    item.name,
                    item.userid,
                    item.subordinate_source,
                    str(len(item.subordinate_names)),
                    "、".join(item.subordinate_names) or "无",
                    "、".join(item.subordinate_missing_names) or "无",
                    item.subordinate_status,
                )
            )
            + " |"
        )
    lines.append("")
    return lines
