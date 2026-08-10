package com.qian.qianaiagent.advisor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import reactor.core.publisher.Flux;

/**
 * 自定义日志 Advisor
 * 流式模式下直接透传，不聚合（聚合会缓存全部 chunk 导致卡顿）
 */
@Slf4j
public class MyLoggerAdvisor implements CallAdvisor, StreamAdvisor {

	@Override
	public String getName() {
		return this.getClass().getSimpleName();
	}

	@Override
	public int getOrder() {
		return 0;
	}

	@Override
	public ChatClientResponse adviseCall(ChatClientRequest chatClientRequest, CallAdvisorChain chain) {
		log.info("AI Request: {}", chatClientRequest.prompt());
		ChatClientResponse response = chain.nextCall(chatClientRequest);
		log.info("AI Response: {}", response.chatResponse().getResult().getOutput().getText());
		return response;
	}

	@Override
	public Flux<ChatClientResponse> adviseStream(ChatClientRequest chatClientRequest, StreamAdvisorChain chain) {
		log.info("AI Stream Request: chatId={}", chatClientRequest.context().get("chat_memory_conversation_id"));
		// 直接透传，不聚合 —— 聚合会等所有 chunk 到达后才放行，造成流式卡顿
		return chain.nextStream(chatClientRequest);
	}
}
