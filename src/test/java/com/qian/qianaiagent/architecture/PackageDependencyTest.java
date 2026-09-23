package com.qian.qianaiagent.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;

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

    // ================================================================
    // 装配规则守护：ChatModel 不得字段注入
    // ================================================================
    //
    // 为什么这条规则值得放在架构测试里，而不是某个类的单元测试里：
    //
    // ResilientChatModel 靠 @Primary 生效。@Resource 会「先按字段名匹配」，
    // 于是 @Resource private ChatModel openAiChatModel; 会在 @Primary 之前
    // 命中那个同名裸 bean —— 该路径完全绕过超时/重试/熔断/降级，且不报错。
    // 这是个「写起来最自然、后果完全隐形」的回归，本次修复的起因就是它。
    //
    // 之前这条约束写在 ResilientChatModelConfigTest 里，用反射硬编码了 4 个类名 ——
    // 新写一个 @Resource ChatModel 字段的类根本不会被它扫到。改为全局规则后，
    // 任何包下新增的违规字段都会让本测试变红。
    //
    // 只认这两种注解：构造器注入（无注解的 final 字段）与 @Bean 方法参数不受影响。

    /**
     * {@code @Resource} 按字段名优先解析，会绕过 {@code @Primary}。
     */
    @Test
    @DisplayName("任何 ChatModel 字段都不得用 @Resource（会按字段名绕过 @Primary）")
    void chatModel_fields_must_not_be_resource_injected() {
        noFields()
                .that().haveRawType(ChatModel.class)
                .should().beAnnotatedWith(Resource.class)
                .check(CLASSES);
    }

    /**
     * {@code @Autowired} 字段注入本身不会绕过 {@code @Primary}，但它与构造器注入混用
     * 会让「这个类到底注入了哪个 ChatModel」变得依赖解析时机；统一走构造器注入，
     * 规则简单到可以一刀切。
     */
    @Test
    @DisplayName("任何 ChatModel 字段都不得用 @Autowired")
    void chatModel_fields_must_not_be_autowired() {
        noFields()
                .that().haveRawType(ChatModel.class)
                .should().beAnnotatedWith(Autowired.class)
                .check(CLASSES);
    }
}
