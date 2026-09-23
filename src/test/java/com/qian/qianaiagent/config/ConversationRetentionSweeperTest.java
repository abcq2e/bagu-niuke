package com.qian.qianaiagent.config;

import com.qian.qianaiagent.interview.progress.ActiveSpecManager;
import com.qian.qianaiagent.memory.FileBasedChatMemory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Clock;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 保留策略清扫时，挂在被删会话下面的「当前项目描述」必须一并清除。
 *
 * <p>为什么值得单独测：两边的删除语义是分开的 ——
 * {@code data/chat-memory/} 有「每用户最多 N 个会话」的配额兜底，
 * 而 {@code .active-specs/} 除 {@code ActiveSpecManager.remove()} 之外没有任何删除路径。
 * 不在清扫器里收尾，它就是一个单调增长的孤儿目录（而且用户删了会话，
 * 项目描述下次还会被 {@code loadSpec} 从盘上「复活」）。
 *
 * <p>每个「该删」的用例都配了「不该删」的反向断言 —— 只断言「删掉了」
 * 的话，一个把 .active-specs 整个清空的实现也能全绿。
 */
class ConversationRetentionSweeperTest {

    @TempDir
    Path root;

    /** 一个「很久以前」的基准时间（2020 年），确保超出保留天数与宽限期。 */
    private static final long OLD = 1_600_000_000_000L;

    private FileBasedChatMemory memory;
    private ActiveSpecManager specs;

    /** 按给定策略装配一套真实的 memory + ActiveSpecManager + 清扫器。 */
    private ConversationRetentionSweeper sweeper(boolean enabled) {
        StorageProperties storage = new StorageProperties();
        storage.setRoot(root.toString());
        storage.setActiveSpec(".active-specs");
        storage.getRetention().setEnabled(enabled);
        storage.getRetention().setMaxAgeDays(30);          // 只按时间删，不按数量
        storage.getRetention().setGracePeriodMinutes(0);   // 关掉宽限期

        // mtime 显式设成 OLD（2020 年），时钟留真实时间即可判定「太老」
        memory = new FileBasedChatMemory(storage.chatMemoryPath().toString(), Clock.systemDefaultZone());

        specs = new ActiveSpecManager();
        ReflectionTestUtils.setField(specs, "storage", storage);
        specs.init();

        return new ConversationRetentionSweeper(memory, storage, specs);
    }

    /** 造一个属于 owner 的会话（含消息，否则没有可删的文件），并把 mtime 固定。 */
    private void conversation(String chatId, Long owner, long epochMillis) throws Exception {
        memory.startSession(chatId, owner);
        memory.add(chatId, List.of(new UserMessage("问")));
        Files.setLastModifiedTime(storageRoot().resolve(chatId + ".json"), FileTime.fromMillis(epochMillis));
    }

    private Path storageRoot() {
        return root.resolve("data/chat-memory");
    }

    private Path specFile(String chatId) {
        return root.resolve(".active-specs").resolve(chatId + ".json");
    }

    @Test
    void sweptConversationTakesItsActiveSpecWithIt() throws Exception {
        ConversationRetentionSweeper sweeper = sweeper(true);
        conversation("chat_ancient", 7L, OLD);
        specs.updateSpec("chat_ancient", "这个会话要被保留策略清掉");

        assertThat(storageRoot().resolve("chat_ancient.json")).exists();
        assertThat(specFile("chat_ancient")).exists();

        sweeper.sweep();

        assertThat(memory.ownerOf("chat_ancient")).isNull();
        assertThat(storageRoot().resolve("chat_ancient.json")).doesNotExist();
        assertThat(specFile("chat_ancient"))
                .as("会话被清掉了，它的项目描述不能留成孤儿文件")
                .doesNotExist();
        // 再从盘上读一次：文件还在的话 loadSpec 会把它「复活」
        assertThat(specs.getSpec("chat_ancient")).isNull();
    }

    @Test
    void survivingConversationKeepsItsActiveSpec() throws Exception {
        ConversationRetentionSweeper sweeper = sweeper(true);
        conversation("chat_ancient", 7L, OLD);
        conversation("chat_recent", 7L, System.currentTimeMillis() - 60_000L);
        specs.updateSpec("chat_ancient", "待清掉的项目描述");
        specs.updateSpec("chat_recent", "应当保留的项目描述");

        sweeper.sweep();

        assertThat(specFile("chat_ancient")).doesNotExist();
        assertThat(specFile("chat_recent"))
                .as("只删被保留策略清掉的会话 —— 不能顺手清空整个 .active-specs")
                .exists();
        assertThat(specs.getSpec("chat_recent")).isEqualTo("应当保留的项目描述");
    }

    @Test
    void anotherUsersSurvivingSessionIsNotSweptAlong() throws Exception {
        ConversationRetentionSweeper sweeper = sweeper(true);
        conversation("chat_mine_old", 7L, OLD);
        conversation("chat_theirs_recent", 9L, System.currentTimeMillis() - 60_000L);
        specs.updateSpec("chat_mine_old", "我的旧会话，该清");
        specs.updateSpec("chat_theirs_recent", "别人的会话，留着");

        // 清扫器一次遍历所有 owner：差集必须按 userId 各自算，不能串台
        sweeper.sweep();

        assertThat(specFile("chat_mine_old")).doesNotExist();
        assertThat(specs.getSpec("chat_theirs_recent")).isEqualTo("别人的会话，留着");
    }

    @Test
    void nothingIsTouchedWhileRetentionIsDisabled() throws Exception {
        ConversationRetentionSweeper sweeper = sweeper(false);
        conversation("chat_ancient", 7L, OLD);
        specs.updateSpec("chat_ancient", "保留策略没开，谁也不许动");

        sweeper.sweep();

        assertThat(specFile("chat_ancient")).exists();
        assertThat(specs.getSpec("chat_ancient")).isEqualTo("保留策略没开，谁也不许动");
    }
}
