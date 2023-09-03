package com.haifoj.haifojcodesandbox.security;

import java.security.Permission;

/**
 * 禁止所有的权限安全管理器
 */
public class DenySecurityManager extends SecurityManager {

    // 检查所有的权限
    @Override
    public void checkPermission(Permission perm) {
        System.out.println("异常抛出：" + perm.getActions());
        throw new SecurityException("权限不足");
    }
}
