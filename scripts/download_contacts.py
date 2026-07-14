from __future__ import annotations

from collections import deque
from typing import Any

from dingtalk_common import DingTalkError, get_access_token, load_env, output_root, topapi_post, write_json


def list_sub_departments(access_token: str, dept_id: int) -> list[dict[str, Any]]:
    result = topapi_post("/topapi/v2/department/listsub", {"dept_id": dept_id}, access_token)
    return result.get("result", []) or []


def list_department_users(access_token: str, dept_id: int) -> list[dict[str, Any]]:
    users: list[dict[str, Any]] = []
    cursor = 0
    while True:
        result = topapi_post(
            "/topapi/v2/user/list",
            {"dept_id": dept_id, "cursor": cursor, "size": 100, "language": "zh_CN"},
            access_token,
        ).get("result", {})
        users.extend(result.get("list", []) or [])
        if not result.get("has_more"):
            break
        cursor = int(result.get("next_cursor", 0))
    return users


def download_contacts(access_token: str, root_dept_id: int) -> tuple[list[dict[str, Any]], list[dict[str, Any]]]:
    departments: list[dict[str, Any]] = []
    users_by_id: dict[str, dict[str, Any]] = {}
    queue: deque[int] = deque([root_dept_id])

    while queue:
        dept_id = queue.popleft()
        for user in list_department_users(access_token, dept_id):
            userid = user.get("userid")
            if userid:
                users_by_id[str(userid)] = user

        children = list_sub_departments(access_token, dept_id)
        departments.extend(children)
        for dept in children:
            child_id = dept.get("dept_id")
            if child_id is not None:
                queue.append(int(child_id))

    return list(users_by_id.values()), departments


def main() -> int:
    try:
        env = load_env()
        root_dept_id = int(env.get("DINGTALK_ROOT_DEPT_ID", "1"))
        access_token = get_access_token(env)

        users, departments = download_contacts(access_token, root_dept_id)

        out_dir = output_root(env) / "contacts"
        write_json(out_dir / "departments.json", departments)
        write_json(out_dir / "users.json", users)
    except DingTalkError as exc:
        print(f"FAILED: {exc}")
        return 1

    print(f"OK: downloaded {len(users)} users and {len(departments)} departments.")
    print(f"Output: {out_dir}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
