package com.sprinkleclaw.ext.skill;

import java.util.*;

/**
 * Skill 注册表，按名称索引已加载的 Skill。
 *
 * @author sprinkle
 * @since 2026/4/3
 */
public final class SkillRegistry {

    private final Map<String, SkillEntry> skills = new LinkedHashMap<>();

    /**
     * 注册一个 Skill。若已存在同名 Skill 则覆盖。
     */
    public void register(SkillEntry entry) {
        skills.put(entry.name(), entry);
    }

    /**
     * 按名称查找 Skill。
     */
    public Optional<SkillEntry> find(String name) {
        return Optional.ofNullable(skills.get(name));
    }

    /**
     * 获取所有已注册 Skill（保持插入顺序）。
     */
    public Collection<SkillEntry> all() {
        return Collections.unmodifiableCollection(skills.values());
    }

    /**
     * 获取所有已注册 Skill 名称。
     */
    public Set<String> names() {
        return Collections.unmodifiableSet(skills.keySet());
    }

    /**
     * 获取已注册 Skill 数量。
     */
    public int size() {
        return skills.size();
    }

    /**
     * 是否为空。
     */
    public boolean isEmpty() {
        return skills.isEmpty();
    }
}
