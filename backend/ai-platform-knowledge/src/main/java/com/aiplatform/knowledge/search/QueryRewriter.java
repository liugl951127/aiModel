package com.aiplatform.knowledge.search;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 查询改写器：把口语化 / 长尾 query 转成更利于 ES / 向量检索的多个变体。
 *
 * <h2>改写策略</h2>
 * <ol>
 *   <li>中文分词（粗粒度）：按标点 + 停用词切，再加原 query</li>
 *   <li>英文 tokenization：split on whitespace + lower-case</li>
 *   <li>中英混合：拆 word + 单字</li>
 *   <li>同义词扩展：内置一个"AI 平台" → [AI 平台, 智能体, Agent, 人工智能] 的字典</li>
 *   <li>去重：保留顺序</li>
 * </ol>
 *
 * <p>改写后用 OR 关系联合搜索，提高召回；再用 ES 排序或 reranker 截断。</p>
 */
@Slf4j
@Component
public class QueryRewriter {

    private static final Pattern PUNCT = Pattern.compile("[\\p{Z}\\p{P}]+");
    private static final List<String> STOP = Arrays.asList(
            "的", "了", "是", "我", "你", "他", "在", "和", "与", "或", "一个", "一下",
            "什么", "怎么", "如何", "请问", "可以", "吗", "呢", "啊", "吧");

    /** 内置同义词字典（小、保守）。生产建议接到词典服务。 */
    private static final List<String[]> SYNONYMS = new java.util.ArrayList<>();
    static {
        SYNONYMS.add(new String[]{"AI 平台", "ai platform"});
        SYNONYMS.add(new String[]{"AI 平台", "智能体平台"});
        SYNONYMS.add(new String[]{"智能体", "Agent", "agent"});
        SYNONYMS.add(new String[]{"RAG", "检索增强"});
        SYNONYMS.add(new String[]{"防幻觉", "幻觉", "hallucination"});
        SYNONYMS.add(new String[]{"Spring Cloud", "spring cloud", "springcloud"});
        SYNONYMS.add(new String[]{"DJL", "djl", "Deep Java Library"});
        SYNONYMS.add(new String[]{"LLM", "llm", "大语言模型", "大模型"});
        SYNONYMS.add(new String[]{"知识库", "KB", "kb", "知识图谱"});
        SYNONYMS.add(new String[]{"训练", "train", "training"});
        SYNONYMS.add(new String[]{"推理", "inference", "infer"});
    }

    /**
     * 改写查询，返回多个变体。
     */
    public List<String> rewrite(String original) {
        Set<String> out = new LinkedHashSet<>();
        if (original == null) return List.of();
        out.add(original);
        // 1. 标点切分
        String[] tokens = PUNCT.split(original.trim());
        for (String t : tokens) {
            if (t.length() < 2) continue;
            if (STOP.contains(t.toLowerCase())) continue;
            out.add(t);
        }
        // 2. 连续中文字符切成 2-gram 提升召回
        for (String t : tokens) {
            if (t.length() >= 4 && isMostlyChinese(t)) {
                for (int i = 0; i + 2 <= t.length(); i++) {
                    out.add(t.substring(i, i + 2));
                }
            }
        }
        // 3. 同义词扩展
        for (String[] group : SYNONYMS) {
            for (String key : group) {
                if (containsIgnoreCase(original, key)) {
                    out.addAll(Arrays.asList(group));
                    break;
                }
            }
        }
        if (out.size() > 8) {  // 控制变体数
            List<String> first = new ArrayList<>(out).subList(0, 8);
            return first;
        }
        log.debug("[REWRITE] {} -> {} variants", original, out.size());
        return new ArrayList<>(out);
    }

    private static boolean containsIgnoreCase(String s, String k) {
        return s.toLowerCase().contains(k.toLowerCase());
    }

    private static boolean isMostlyChinese(String s) {
        int cjk = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= 0x4E00 && c <= 0x9FFF) cjk++;
        }
        return cjk >= s.length() * 0.5;
    }

    /** 内部用：jaccard 相似度（两段文本公共 token / 全部 token）。 */
    public static double jaccard(String a, String b) {
        if (a == null || b == null) return 0;
        Set<String> ta = new LinkedHashSet<>(Arrays.asList(PUNCT.split(a.toLowerCase().trim())));
        Set<String> tb = new LinkedHashSet<>(Arrays.asList(PUNCT.split(b.toLowerCase().trim())));
        ta.removeAll(STOP); tb.removeAll(STOP);
        if (ta.isEmpty() || tb.isEmpty()) return 0;
        Set<String> inter = new LinkedHashSet<>(ta); inter.retainAll(tb);
        Set<String> union = new LinkedHashSet<>(ta); union.addAll(tb);
        return (double) inter.size() / union.size();
    }
}
