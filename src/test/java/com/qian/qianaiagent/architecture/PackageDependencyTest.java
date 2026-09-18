package com.qian.qianaiagent.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * 包依赖方向守护测试 —— 防止循环依赖回潮。
 * <p>
 * 目标单向依赖（解环后的既定方向）：
 * <pre>
 *   catalog(叶子) ← knowledge ← ability ← interview/evaluation/rag/agent ← controller
 * </pre>
 * 任何违反下述方向的改动（如 knowledge 重新 import ability / interview）都会让测试红。
 */
class PackageDependencyTest {

    private static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages("com.qian.qianaiagent");

    private static ArchRule noCrossDomainDependency(String source, String target) {
        return noClasses()
                .that().resideInAPackage(source)
                .should().dependOnClassesThat().resideInAnyPackage(target);
    }

    @Test
    void catalog_is_a_leaf_package() {
        // 内核叶子：不得依赖任何其它领域包（catalog 内类仅依赖 java.*）
        ArchRule rule = noClasses()
                .that().resideInAPackage("com.qian.qianaiagent.catalog..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.qian.qianaiagent.ability..",
                        "com.qian.qianaiagent.interview..",
                        "com.qian.qianaiagent.knowledge..",
                        "com.qian.qianaiagent.rag..",
                        "com.qian.qianaiagent.evaluation..",
                        "com.qian.qianaiagent.agent..",
                        "com.qian.qianaiagent.controller..",
                        "com.qian.qianaiagent.tools..",
                        "com.qian.qianaiagent.service..");
        rule.check(CLASSES);
    }

    @Test
    void knowledge_must_not_depend_on_ability_or_interview() {
        noCrossDomainDependency("com.qian.qianaiagent.knowledge..", "com.qian.qianaiagent.ability..")
                .check(CLASSES);
        noCrossDomainDependency("com.qian.qianaiagent.knowledge..", "com.qian.qianaiagent.interview..")
                .check(CLASSES);
    }

    @Test
    void ability_must_not_depend_on_interview_rag_agent_or_controller() {
        noCrossDomainDependency("com.qian.qianaiagent.ability..", "com.qian.qianaiagent.interview..").check(CLASSES);
        noCrossDomainDependency("com.qian.qianaiagent.ability..", "com.qian.qianaiagent.rag..").check(CLASSES);
        noCrossDomainDependency("com.qian.qianaiagent.ability..", "com.qian.qianaiagent.agent..").check(CLASSES);
        noCrossDomainDependency("com.qian.qianaiagent.ability..", "com.qian.qianaiagent.controller..").check(CLASSES);
    }

    @Test
    void rag_must_not_depend_on_interview_evaluation_or_ability() {
        noCrossDomainDependency("com.qian.qianaiagent.rag..", "com.qian.qianaiagent.interview..").check(CLASSES);
        noCrossDomainDependency("com.qian.qianaiagent.rag..", "com.qian.qianaiagent.evaluation..").check(CLASSES);
        noCrossDomainDependency("com.qian.qianaiagent.rag..", "com.qian.qianaiagent.ability..").check(CLASSES);
    }

    @Test
    void interview_must_not_depend_on_controller() {
        noCrossDomainDependency("com.qian.qianaiagent.interview..", "com.qian.qianaiagent.controller..").check(CLASSES);
    }
}
