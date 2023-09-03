package com.haifoj.haifojcodesandbox.security;

import java.security.Permission;

/**
 * 默认安全管理器
 */
public class DefaultSecurityManager extends SecurityManager {

    // 检查所有的权限
    @Override
    public void checkPermission(Permission perm) {
        System.out.println("默认不做任何限制：" + perm);
//        super.checkPermission(perm); 要注释掉才可以，不做任何限制
    }

}
