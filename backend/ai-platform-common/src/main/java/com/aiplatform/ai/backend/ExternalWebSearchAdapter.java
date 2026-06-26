package com.aiplatform.ai.backend;

import java.util.List;

/**
 * ★ 外部联网搜索适配器接口 (不取代, 跟内部实现并存)
 *
 * <p>本接口让业务侧:
 * <ul>
 *   <li>默认走 <b>内部</b> (本系统知识库 + 内置 corpus, 0 联网)</li>
 *   <li>需要时切 <b>外部</b> (DuckDuckGo / Bing / Google CSE 等, 联网)</li>
 * </ul>
 *
 * <p>实现:
 * <ul>
 *   <li>{@code DuckDuckGoSearchAdapter}  (默认实现, 公开免费, 需联网)</li>
 *   <li>可扩展: BingSearchAdapter, GoogleCSE, TavilyAI 等</li>
 * </ul>
 *
 * <p>激活: application.yml 配 {@code aiplatform.ai.search-mode=external}
 *        + 配外部 adapter (默认就启 DuckDuckGo)</p>
 */
public interface ExternalWebSearchAdapter {

    String name();

    /** 适配器是否可用 (配置 + 网络通) */
    boolean isAvailable();

    /** 执行外部搜索, 失败抛异常让上层降级 */
    List<AIBackend.WebSearchResult> search(String query, int topK);
}
