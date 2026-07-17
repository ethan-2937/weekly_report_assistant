package com.yzzhang.weeklyreport.service.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class TemplateCompliancePolicy {
    private static final List<String> TECH_PRODUCT_TITLE_KEYWORDS = List.of(
        "技术", "研发", "开发", "测试", "运维", "架构", "算法", "实施", "工程师", "产品"
    );
    private static final List<String> SALES_TITLE_KEYWORDS = List.of(
        "销售", "商务", "售前", "客户经理"
    );

    private static final List<RequiredField> COMMON_PREFIX = List.of(
        field("本周完成成果", "本周完成成果", "本周成果"),
        field("工时投入分析", "工时投入分析", "工时占比", "时间投入分析", "时间分配"),
        field("AI应用及效果", "AI应用及效果", "AI应用", "AI使用")
    );
    private static final List<RequiredField> PROJECT_AND_EXPENSE = List.of(
        field("产品线", "归属于哪条产品线", "产品线"),
        field("客户名称", "本周服务的客户名称", "客户名称"),
        field("项目名称", "本周服务的项目名称", "项目名称"),
        field("本周差旅费用", "本周您产生的差旅费用", "本周产生的差旅费用", "差旅费用"),
        field("本周招待费用", "本周您产生的招待费用", "本周产生的招待费用", "招待费用")
    );
    private static final List<RequiredField> WORKDAY_DETAILS = List.of(
        field("本周投入工时合计（天）", "本周投入工时合计天", "本周投入工时合计"),
        field("本周投入工时（天）明细分布", "本周投入工时天明细分布", "投入工时明细分布")
    );
    private static final RequiredField NEXT_PLAN = field(
        "下周计划（含交付时间）", "下周计划含交付时间", "下周计划"
    );

    private TemplateCompliancePolicy() {
    }

    static RequirementSet forTitle(String title) {
        String normalizedTitle = normalize(title);
        if (containsAny(normalizedTitle, TECH_PRODUCT_TITLE_KEYWORDS)) {
            return requirements("技术/产品岗位", true);
        }
        if (containsAny(normalizedTitle, SALES_TITLE_KEYWORDS)) {
            return requirements("销售岗位", false);
        }
        return new RequirementSet("通用岗位", append(COMMON_PREFIX, List.of(NEXT_PLAN)));
    }

    private static RequirementSet requirements(String label, boolean includeWorkdays) {
        List<RequiredField> fields = new ArrayList<>(COMMON_PREFIX);
        fields.addAll(PROJECT_AND_EXPENSE.subList(0, 3));
        if (includeWorkdays) {
            fields.addAll(WORKDAY_DETAILS);
        }
        fields.addAll(PROJECT_AND_EXPENSE.subList(3, PROJECT_AND_EXPENSE.size()));
        fields.add(NEXT_PLAN);
        return new RequirementSet(label, List.copyOf(fields));
    }

    private static List<RequiredField> append(List<RequiredField> left, List<RequiredField> right) {
        List<RequiredField> fields = new ArrayList<>(left);
        fields.addAll(right);
        return List.copyOf(fields);
    }

    private static boolean containsAny(String title, List<String> keywords) {
        return !title.isBlank() && keywords.stream().map(TemplateCompliancePolicy::normalize).anyMatch(title::contains);
    }

    private static RequiredField field(String label, String... aliases) {
        return new RequiredField(label, List.of(aliases));
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("[\\s　（）()【】\\[\\]，,。；;：:、/\\\\\\-]", "")
            .toLowerCase(Locale.ROOT);
    }

    record RequiredField(String label, List<String> aliases) {
    }

    record RequirementSet(String label, List<RequiredField> fields) {
    }
}
