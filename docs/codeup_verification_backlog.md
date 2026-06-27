# 云效 Codeup 代码提交核验待办

## 背景

后续希望针对开发人员增加一项校验：员工周报里写到的开发工作，是否能和云效 Codeup 的提交记录、合并记录、代码库活动大致对应上。

## 实现思路

1. 先通过云效/Codeup OpenAPI 获取组织下的代码库清单，避免手工逐个维护仓库地址。
2. 按周报周期拉取每个代码库的 commit、merge request、分支等活动记录。
3. 使用 `authorEmail`、`committerEmail`、`authorName`、`committerName` 或云效 userId 与员工通讯录做映射。
4. 为每名开发人员生成当周代码活动摘要，例如提交次数、涉及仓库、主要文件、合并请求和异常空窗。
5. 将原始核验数据写入 `output/<YYYY-Www>/codeup/`，并把摘要追加到 `analysis/analysis_input.md`，交给 Codex skill 做评价。
6. 前端增加“周报与代码提交一致性”展示区，标记需要人工复核的员工。

## 需要准备的配置

```text
YUNXIAO_EDITION=central 或 region
YUNXIAO_DOMAIN=https://codeup.aliyun.com
YUNXIAO_ORGANIZATION_ID=684beabd28a6beb51d765af2
YUNXIAO_TOKEN=云效个人访问令牌，存放在 config/.env，不能提交到代码仓库
```

## 暂缓原因

当前先完成周报采集、AI 总结和 Web 展示闭环。Codeup 核验需要确认：

- 云效 OpenAPI 权限是否已开通。
- 组织下代码库、成员、邮箱或 userId 的映射规则。
- token 的最小权限和安全保存方式。

## 后续风险点

- 员工可能使用不同邮箱或账号提交代码，需要建立通讯录和 Codeup 账号映射表。
- 仅靠 commit 数不能直接评价工作质量，需要结合周报、合并请求、代码评审和任务系统综合判断。
- 有些开发工作可能是设计、排查、评审或线上支持，代码提交较少但仍然有效，需要保留人工复核入口。
