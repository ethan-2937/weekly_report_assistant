from __future__ import annotations

from dingtalk_common import DingTalkError, get_access_token, load_env


def main() -> int:
    try:
        load_env()
        token = get_access_token()
    except DingTalkError as exc:
        print(f"FAILED: {exc}")
        return 1

    print("OK: DingTalk access token fetched.")
    print(f"Token preview: {token[:6]}...{token[-4:]}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

