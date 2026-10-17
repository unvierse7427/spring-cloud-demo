package com.example.rag.support;

import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 文档可见角色判定。
 *
 * <p>文档上的 visibleRoles 为空表示所有登录用户可见；
 * 非空时，只有权限集合与之存在交集的用户才能查看该文档（含列表、预览、下载与问答检索）。
 */
public final class DocumentPermission {

    private static final String SEPARATOR = ",";

    private DocumentPermission() {
    }

    /** 解析逗号分隔的角色码，空值返回空列表 */
    public static List<String> parse(String roles) {
        if (!StringUtils.hasText(roles)) {
            return List.of();
        }
        return Arrays.stream(roles.split(SEPARATOR))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
    }

    /** 把角色码拼成入库用的字符串，空集合返回空串（即所有人可见） */
    public static String join(Collection<String> roles) {
        if (roles == null || roles.isEmpty()) {
            return "";
        }
        return roles.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .collect(Collectors.joining(SEPARATOR));
    }

    /** 判断持有给定权限集合的用户能否查看该文档 */
    public static boolean canView(String visibleRoles, Collection<String> authorities) {
        List<String> required = parse(visibleRoles);
        if (required.isEmpty()) {
            return true;
        }
        if (authorities == null || authorities.isEmpty()) {
            return false;
        }
        return required.stream().anyMatch(authorities::contains);
    }
}
