package org.twelve.aipp.skill;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AippSkillPackagesTest {

    @Test
    void discoverAndBuildIndexFromClasspathSkill() throws Exception {
        ClassLoader cl = AippSkillPackagesTest.class.getClassLoader();
        List<String> ids = AippSkillPackages.discoverSkillIds(cl);
        assertThat(ids).contains("demo_skill");

        String md = AippSkillPackages.readPlaybook(cl, "demo_skill");
        assertThat(md).isNotNull().contains("Demo Skill");

        Map<String, Object> entry = AippSkillPackages.buildIndexEntry("demo_skill", md, "demo-app");
        assertThat(entry.get("name")).isEqualTo("demo_skill");
        assertThat(entry.get("description").toString()).contains("Use when");
        assertThat(entry.get("level")).isEqualTo("widget");
        assertThat(entry.get("owner_widget")).isEqualTo("demo_widget");
        @SuppressWarnings("unchecked")
        List<String> tools = (List<String>) entry.get("allowed_tools");
        assertThat(tools).containsExactly("demo_tool");

        List<Map<String, Object>> files = AippSkillPackages.listPackageFiles(cl, "demo_skill");
        assertThat(files).isNotEmpty();
        assertThat(files.stream().anyMatch(f -> "SKILL.md".equals(f.get("name")))).isTrue();
    }
}
