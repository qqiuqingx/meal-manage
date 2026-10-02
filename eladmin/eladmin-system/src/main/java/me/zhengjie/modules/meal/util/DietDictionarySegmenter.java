package me.zhengjie.modules.meal.util;

import com.hankcs.hanlp.collection.trie.DoubleArrayTrie;
import com.hankcs.hanlp.collection.trie.bintrie.BinTrie;
import com.hankcs.hanlp.corpus.tag.Nature;
import com.hankcs.hanlp.dictionary.CoreDictionary;
import com.hankcs.hanlp.dictionary.DynamicCustomDictionary;
import com.hankcs.hanlp.seg.Segment;
import com.hankcs.hanlp.seg.Viterbi.ViterbiSegment;

import java.util.Collection;
import java.util.TreeMap;

/** 饮食领域独立分词器工厂；每次创建仅使用调用方提供的名称，不修改全局词典。 */
public final class DietDictionarySegmenter {

    private DietDictionarySegmenter() {
    }

    /**
     * 为一轮匹配创建使用内置基础词库、强制领域词匹配和词偏移的 HanLP 分词器。
     * @param names 已规整的领域名称集合；空集合关闭自定义词典
     * @return 独立可变实例，仅供本轮单线程使用，不跨请求共享
     */
    public static Segment create(Collection<String> names) {
        Segment segment = new ViterbiSegment().enableAllNamedEntityRecognize(false).enableOffset(true);
        TreeMap<String, CoreDictionary.Attribute> words = new TreeMap<>();
        for (String name : names) {
            if (name != null && !name.isEmpty()) {
                words.put(name, new CoreDictionary.Attribute(Nature.nz, 1024));
            }
        }
        if (words.isEmpty()) {
            return segment.enableCustomDictionary(false);
        }
        DynamicCustomDictionary dictionary = new DynamicCustomDictionary(
                new DoubleArrayTrie<>(words), new BinTrie<>(), null);
        return segment.enableCustomDictionary(dictionary).enableCustomDictionaryForcing(true);
    }
}
