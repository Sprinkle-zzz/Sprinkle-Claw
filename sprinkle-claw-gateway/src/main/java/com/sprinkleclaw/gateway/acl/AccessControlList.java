package com.sprinkleclaw.gateway.acl;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * IP 黑白名单配置。
 *
 * @author sprinkle
 * @since 0.7.0 (MVP6)
 */
public class AccessControlList {

    public enum DefaultPolicy { ALLOW, DENY }

    private final DefaultPolicy defaultPolicy;
    private final Set<String> whitelist = new CopyOnWriteArraySet<>();
    private final Set<String> blacklist = new CopyOnWriteArraySet<>();

    public AccessControlList(DefaultPolicy defaultPolicy) {
        this.defaultPolicy = defaultPolicy;
    }

    public AccessControlList(DefaultPolicy defaultPolicy, List<String> whitelist, List<String> blacklist) {
        this.defaultPolicy = defaultPolicy;
        this.whitelist.addAll(whitelist);
        this.blacklist.addAll(blacklist);
    }

    /**
     * 判定是否允许访问。
     */
    public boolean isAllowed(String remoteAddress) {
        if (blacklist.contains(remoteAddress)) {
            return false;
        }
        if (!whitelist.isEmpty()) {
            return whitelist.contains(remoteAddress);
        }
        return defaultPolicy == DefaultPolicy.ALLOW;
    }

    public void addToWhitelist(String address) {
        whitelist.add(address);
    }

    public void addToBlacklist(String address) {
        blacklist.add(address);
    }
}
