package me.zhengjie.modules.customer.profile.service;

import com.alibaba.fastjson2.JSON;
import com.hankcs.hanlp.collection.trie.DoubleArrayTrie;
import com.hankcs.hanlp.collection.trie.bintrie.BinTrie;
import com.hankcs.hanlp.corpus.tag.Nature;
import com.hankcs.hanlp.dictionary.CoreDictionary;
import com.hankcs.hanlp.dictionary.DynamicCustomDictionary;
import com.hankcs.hanlp.seg.Segment;
import com.hankcs.hanlp.seg.Viterbi.ViterbiSegment;
import com.hankcs.hanlp.seg.common.Term;
import lombok.RequiredArgsConstructor;
import me.zhengjie.modules.customer.profile.domain.CustomerDietImportData;
import me.zhengjie.modules.customer.profile.domain.ImportCandidate;
import me.zhengjie.modules.customer.profile.domain.ParsedCustomer;
import me.zhengjie.modules.customer.profile.domain.ParsedWorkbook;
import me.zhengjie.modules.customer.profile.domain.CustomerDietSourceRow;
import me.zhengjie.modules.customer.profile.domain.dto.CustomerDietItemDto;
import me.zhengjie.modules.customer.profile.domain.dto.CustomerDietMatchDto;
import me.zhengjie.modules.customer.profile.domain.dto.CustomerDietOptionDto;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 第二工作表饮食原文的精确匹配、全部候选应用和饮食字典摘要服务。
 */
@Service
@RequiredArgsConstructor
public class CustomerDietMatchService {


    /** 词项分隔符（文本已先做 NFKC 归一化，全角标点此时已是半角）。 */
    private static final Pattern ITEM_SEPARATOR = Pattern.compile("[、,;:。.·/\\s&+|!?()]+");
    /** 括号内的补充说明，如「海鲜（除鱼虾）」「[偷笑]」「【饮食禁忌】」，拆词前整体去掉。 */
    private static final Pattern ANNOTATION = Pattern.compile("\\([^)]*\\)|\\[[^\\]]*\\]|【[^】]*】|「[^」]*」");
    private static final Pattern ZERO_WIDTH = Pattern.compile("[\\u200B-\\u200D\\u2060\\uFEFF\\uFE0F]");
    private static final Pattern SYMBOLS = Pattern.compile("\\p{So}");
    private static final Pattern EDGE_PUNCTUATION = Pattern.compile("^[\\p{P}\\p{S}\\s]+|[\\p{P}\\p{S}\\s]+$");
    private static final Pattern PURE_NUMBER = Pattern.compile("\\d+(?:\\.\\d+)?");

    /** 否定表达（客户不想吃）。长的写在前面，Java 正则按顺序而不是最长匹配。 */
    private static final String AVOID_CORE = "不(?:太|是很|怎么|特别|大)?(?:喜欢吃?|爱吃|想吃|能吃|可以吃|要吃)"
            + "|不吃|不要吃?|忌口|禁止|禁用|避免|拒绝|少安排|少吃";
    /** 肯定表达（客户想吃）。 */
    private static final String WANT_CORE = "喜欢吃?|爱吃|想吃|要吃|多安排|多吃";
    /** 词首前缀：允许「忌」「禁忌」「过敏食物」这类标签式前缀。 */
    private static final Pattern AVOID_PREFIX = Pattern.compile("^(?:" + AVOID_CORE + "|禁忌|忌|过敏(?:食物|源|原)?)");
    private static final Pattern WANT_PREFIX = Pattern.compile("^(?:" + WANT_CORE + ")");
    /** 词中粘连拆分用：不含单字「忌」和「禁忌」，避免把「痛风禁忌」拆坏。 */
    private static final Pattern AVOID_GLUE = Pattern.compile("(?:" + AVOID_CORE + ")");
    private static final Pattern WANT_GLUE = Pattern.compile("(?:" + WANT_CORE + ")");
    /** 后缀：山药过敏、虾有轻微过敏、金耳过敏一定注意点缀。 */
    private static final Pattern ALLERGY_SUFFIX = Pattern.compile("^(.+?)有?(?:轻微|轻度|严重|一点)?(?:过敏|不耐受)");
    private static final Pattern AVOID_SUFFIX = Pattern.compile("^(.+?)(?:不吃|不能吃|不要|不喜欢|不爱吃|忌口)$");
    private static final Pattern WANT_SUFFIX = Pattern.compile("^(.+?)(?:想吃|爱吃|喜欢吃?)$");
    /** 并列连接词，仅在整词查不到字典时才拆。 */
    private static final Pattern CONNECTOR = Pattern.compile("以及|还有|或者|和|及|与|或|跟");
    /** 查字典前可以去掉的修饰：「所有的鱼」「各类内脏」「辣的」「肥腻食物」「花菜类」。整词能查到字典时不会去。 */
    private static final Pattern LOOKUP_NOISE_HEAD = Pattern.compile("^(?:所有的|所有|一切|各类|各种|任何|全部)");
    private static final Pattern LOOKUP_NOISE_TAIL = Pattern.compile("(?:之类|等等|类食物|食物|食品|菜品|饮食|类|等|的|也|都)$");

    /** 一个词项去掉表达前后缀后的正文。 */
    private static final class ParsedTerm {
        private String body;
    }

    /**
     * 计算有序饮食字典快照摘要，确认时用于拒绝过期候选。
     *
     * @param options 当前有效字典选项
     * @return 规范化 JSON 内容的 SHA-256
     */
    public String dictionaryHash(List<CustomerDietOptionDto> options) {
        List<CustomerDietOptionDto> sorted = new ArrayList<>(options);
        sorted.sort(Comparator.comparing(CustomerDietOptionDto::getType)
                .thenComparing(CustomerDietOptionDto::getName)
                .thenComparing(CustomerDietOptionDto::getId));
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                    .digest(JSON.toJSONString(sorted).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(bytes.length * 2);
            for (byte value : bytes) {
                result.append(Character.forDigit((value >>> 4) & 0xF, 16));
                result.append(Character.forDigit(value & 0xF, 16));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("无法计算饮食字典摘要", e);
        }
    }

    /**
     * 将第二工作表来源行按月份客户编号关联、聚合字段并匹配 D/F 原文。
     *
     * @param workbook 月份工作表及第二工作表解析结果
     * @param candidates 已解析的月份客户候选
     * @param options 当前有效饮食字典
     */
    public void attachDietRows(ParsedWorkbook workbook, List<ImportCandidate> candidates,
                               List<CustomerDietOptionDto> options) {
        Map<String, ImportCandidate> byCode = candidates.stream()
                .collect(Collectors.toMap(candidate -> candidate.getParsed().getEffectiveCode(), candidate -> candidate,
                        (first, second) -> first, LinkedHashMap::new));
        Map<String, List<CustomerDietSourceRow>> rowsByCode = new LinkedHashMap<>();
        for (CustomerDietSourceRow sourceRow : workbook.getDietRows()) {
            ImportCandidate candidate = sourceRow.getEffectiveCode() == null
                    ? null : byCode.get(sourceRow.getEffectiveCode());
            if (candidate == null) {
                workbook.getIssues().add(me.zhengjie.modules.customer.profile.domain.dto.CustomerImportIssueDto.of(
                        me.zhengjie.modules.customer.profile.domain.dto.CustomerImportIssueCategory.PROFILE_ERROR,
                        sourceRow.getSourceRow(), sourceRow.getEffectiveCode(),
                        "「客户禁忌」工作表编号未关联到本次导入客户，本行不导入"));
                continue;
            }
            rowsByCode.computeIfAbsent(sourceRow.getEffectiveCode(), key -> new ArrayList<>()).add(sourceRow);
        }

        Map<String, List<CustomerDietOptionDto>> index = buildIndex(options);
        Segment segment = createSegment(index);
        for (Map.Entry<String, List<CustomerDietSourceRow>> entry : rowsByCode.entrySet()) {
            ImportCandidate candidate = byCode.get(entry.getKey());
            CustomerDietImportData data = aggregate(entry.getValue(), index, segment);
            candidate.getParsed().setDietImportData(data);
            mergeWithExisting(candidate, data);
        }
    }

    /**
     * 将每个饮食匹配项的全部候选对象写入对应结构化字段。
     *
     * @param candidates 工作簿全部客户候选
     */
    public void applyAllMatches(List<ImportCandidate> candidates) {
        for (ImportCandidate candidate : candidates) {
            if (!candidate.getParsed().isImportable() || candidate.isAlreadyExists()) {
                continue;
            }
            ParsedCustomer customer = candidate.getParsed();
            CustomerDietImportData data = customer.getDietImportData();
            if (data == null) {
                continue;
            }
            data.getDishRequirements().clear();
            data.getDietaryRestrictions().clear();
            for (CustomerDietMatchDto match : data.getMatches()) {
                List<CustomerDietItemDto> target = "DISH_REQUIREMENTS".equals(match.getSide())
                        ? data.getDishRequirements() : data.getDietaryRestrictions();
                for (CustomerDietItemDto item : match.getSelectedItems()) {
                    appendUnique(target, item);
                }
            }
        }
    }

    /**
     * 合并客户第二工作表的医嘱、日期、术后文本及饮食原文块。
     *
     * @param rows 同一编号下按来源行排序的记录
     * @param index 按名称建好的字典索引
     * @param segment 本次导入独立的领域分词器
     * @return 合并后的导入数据及词项匹配结果
     */
    private CustomerDietImportData aggregate(List<CustomerDietSourceRow> rows,
                                             Map<String, List<CustomerDietOptionDto>> index, Segment segment) {
        CustomerDietImportData data = new CustomerDietImportData();
        Set<String> medical = new LinkedHashSet<>();
        Set<String> postoperative = new LinkedHashSet<>();
        Map<LocalDateTime, List<String>> dealTimes = new LinkedHashMap<>();
        List<String> dealTimeSources = new ArrayList<>();
        for (CustomerDietSourceRow row : rows) {
            medical.add(row.getMedicalRequirements());
            postoperative.add(row.getPostoperativeInfo());
            if (row.getDealTime() != null) {
                dealTimes.computeIfAbsent(row.getDealTime(), key -> new ArrayList<>()).add(row.getDealTimeSource());
            }
            if (row.getDealTimeSource() != null) {
                dealTimeSources.add(row.getDealTimeSource());
            }
            appendUniqueText(data.getDishRequirementsRaw(), row.getDishRequirementsRaw());
            appendUniqueText(data.getDietaryRestrictionsRaw(), row.getDietaryRestrictionsRaw());
            for (String issue : row.getIssues()) {
                data.getIssues().add(issue);
            }
            data.getMatches().addAll(matchCell(row, 4, "DISH_REQUIREMENTS", row.getDishRequirementsRaw(), index, segment));
            data.getMatches().addAll(matchCell(row, 6, "DIETARY_RESTRICTIONS", row.getDietaryRestrictionsRaw(), index, segment));
        }
        data.setMedicalRequirements(singleNonBlank(medical));
        if (nonBlankValues(medical).size() > 1) {
            data.setMedicalConflict("同一客户多行医嘱内容不同，请先核对后重新导入");
        }
        data.setPostoperativeInfo(singleNonBlank(postoperative));
        if (nonBlankValues(postoperative).size() > 1) {
            data.setPostoperativeConflict("同一客户多行术后内容不同，请先核对后重新导入");
        }
        if (dealTimes.size() == 1) {
            data.setDealTime(dealTimes.keySet().iterator().next());
        } else if (dealTimes.size() > 1) {
            data.setDealTimeConflict("同一客户多行成交时间不同，请先核对后重新导入");
        }
        data.setDealTimeSource(String.join("；", new LinkedHashSet<>(dealTimeSources)));
        return data;
    }

    /**
     * 对 D/F 单元格先整段匹配，再清洗、拆词并逐词精确匹配，识别连写的字典名称。
     * <p>
     * 方向固定由来源列决定：D 列表示客户想吃，F 列表示客户禁忌。
     * 饮食表达前缀只用于清洗查找文本，不改变匹配对象的归属方向。
     *
     * @param row 原始来源行
     * @param sourceColumn Excel 列号，1 基
     * @param side 来源列对应的固定方向
     * @param rawText 完整单元格原文
     * @param index 当前字典名称索引
     * @param segment 本次导入独立的领域分词器
     * @return 逐词项匹配结果
     */
    private List<CustomerDietMatchDto> matchCell(CustomerDietSourceRow row, int sourceColumn,
                                                 String side, String rawText,
                                                 Map<String, List<CustomerDietOptionDto>> index, Segment segment) {
        if (isBlank(rawText)) {
            return new ArrayList<>();
        }
        List<CustomerDietOptionDto> exactWholeCell = findOptions(rawText, index);
        if (!exactWholeCell.isEmpty()) {
            return new ArrayList<>(Arrays.asList(createMatch(row, sourceColumn, side, rawText,
                    rawText.trim(), rawText.trim(), 0, exactWholeCell)));
        }
        List<CustomerDietMatchDto> matches = new ArrayList<>();
        int termIndex = 0;
        for (String rawTerm : ITEM_SEPARATOR.split(cleanCell(rawText))) {
            String term = EDGE_PUNCTUATION.matcher(rawTerm).replaceAll("");
            if (term.isEmpty() || PURE_NUMBER.matcher(term).matches()) {
                continue;
            }
            ParsedTerm whole = parseTerm(term, index);
            boolean wholeKnown = whole.body.isEmpty() || !findOptions(whole.body, index).isEmpty();
            List<String> expressions = wholeKnown ? Collections.singletonList(term) : splitConnectedExpressions(term);
            for (String expression : expressions) {
                ParsedTerm parsed = parseTerm(expression, index);
                if (parsed.body.isEmpty()) {
                    continue;
                }
                List<String> pieces = new ArrayList<>();
                for (String connected : splitConnectors(parsed.body, index)) {
                    pieces.addAll(splitKnownFoods(refineLookup(connected, index), index, segment));
                }
                for (String piece : pieces) {
                    String lookup = refineLookup(piece, index);
                    String rawItem = pieces.size() == 1 ? expression : piece;
                    CustomerDietMatchDto match = createMatch(row, sourceColumn, side, rawText,
                            rawItem, lookup, termIndex++, findOptions(lookup, index));
                    matches.add(match);
                }
            }
        }
        return matches;
    }

    /**
     * 拆词前的文本清洗：NFKC 归一化（全角转半角、NBSP 转空格）、去零宽字符、
     * 去括号说明和表情符号。只做字符层面的规整，不涉及任何近似匹配。
     */
    private String cleanCell(String text) {
        String result = canonical(text);
        result = ANNOTATION.matcher(result).replaceAll(" ");
        return SYMBOLS.matcher(result).replaceAll(" ");
    }

    /** 字典名称与查找文本共用的规范形式。 */
    private String canonical(String text) {
        String result = Normalizer.normalize(text, Normalizer.Form.NFKC);
        return ZERO_WIDTH.matcher(result).replaceAll("").trim();
    }

    /**
     * 去除有限表达前后缀并提取正文，不据此改变来源列决定的对象归属方向。
     * 整词本身就是字典名称时不做任何剥离，正文剥完前缀后是字典名称时也不再剥后缀。
     */
    private ParsedTerm parseTerm(String expression, Map<String, List<CustomerDietOptionDto>> index) {
        ParsedTerm term = new ParsedTerm();
        term.body = expression;
        if (!findOptions(expression, index).isEmpty()) {
            return term;
        }
        Matcher matcher = AVOID_PREFIX.matcher(expression);
        boolean hasPrefix = matcher.find();
        if (!hasPrefix) {
            matcher = WANT_PREFIX.matcher(expression);
            hasPrefix = matcher.find();
        }
        if (hasPrefix) {
            term.body = expression.substring(matcher.end()).trim();
        }
        if (term.body.isEmpty() || !findOptions(term.body, index).isEmpty()) {
            return term;
        }
        matcher = ALLERGY_SUFFIX.matcher(term.body);
        if (matcher.find()) {
            term.body = cleanBody(matcher.group(1).trim(), index);
            return term;
        }
        matcher = AVOID_SUFFIX.matcher(term.body);
        if (matcher.find()) {
            term.body = cleanBody(matcher.group(1).trim(), index);
            return term;
        }
        matcher = WANT_SUFFIX.matcher(term.body);
        if (matcher.find()) {
            term.body = cleanBody(matcher.group(1).trim(), index);
        }
        return term;
    }

    /**
     * 按重复出现的明确表达前缀拆开紧邻词项（「不吃猪肉不吃牛肉」）。
     * 从开头前缀之后开始扫描，且每次命中后跳过整个前缀，避免「不太喜欢吃」被从中间拆开。
     * 拆出的残片（如「也」「都」）没有正文，后续会被丢弃。
     *
     * @param term 已按标点与空白拆出的原词项
     * @return 顺序不变的独立表达
     */
    private List<String> splitConnectedExpressions(String term) {
        List<String> expressions = new ArrayList<>();
        int start = 0;
        int index = leadingPrefixLength(term);
        while (index < term.length()) {
            int length = gluePrefixLength(term, index);
            if (length > 0) {
                if (index > start) {
                    expressions.add(term.substring(start, index));
                    start = index;
                }
                index += length;
            } else {
                index++;
            }
        }
        expressions.add(term.substring(start));
        return expressions;
    }

    private int leadingPrefixLength(String text) {
        Matcher matcher = AVOID_PREFIX.matcher(text);
        if (matcher.find()) {
            return matcher.end();
        }
        matcher = WANT_PREFIX.matcher(text);
        return matcher.find() ? matcher.end() : 0;
    }

    private int gluePrefixLength(String text, int from) {
        Matcher matcher = AVOID_GLUE.matcher(text).region(from, text.length());
        if (matcher.lookingAt()) {
            return matcher.end() - from;
        }
        matcher = WANT_GLUE.matcher(text).region(from, text.length());
        return matcher.lookingAt() ? matcher.end() - from : 0;
    }

    /**
     * 按「和/及/与/或…」拆并列词项。整词能查到字典、或连接词在开头/结尾（如「和牛」）时不拆。
     */
    private List<String> splitConnectors(String body, Map<String, List<CustomerDietOptionDto>> index) {
        if (!findOptions(body, index).isEmpty()) {
            return Collections.singletonList(body);
        }
        String[] parts = CONNECTOR.split(body);
        if (parts.length < 2) {
            return Collections.singletonList(body);
        }
        List<String> result = new ArrayList<>();
        for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                return Collections.singletonList(body);
            }
            result.add(trimmed);
        }
        return result;
    }

    /**
     * 为本次导入建立独立的 HanLP 分词器，使用有效字典名称保护领域词，不修改全局词典。
     *
     * @param index 当前有效字典名称索引
     * @return 使用内置基础词库与本次领域词典的分词器
     */
    private Segment createSegment(Map<String, List<CustomerDietOptionDto>> index) {
        Segment segment = new ViterbiSegment().enableAllNamedEntityRecognize(false).enableOffset(true);
        if (index.isEmpty()) {
            return segment.enableCustomDictionary(false);
        }
        TreeMap<String, CoreDictionary.Attribute> words = new TreeMap<>();
        for (String name : index.keySet()) {
            if (!name.isEmpty()) {
                words.put(name, new CoreDictionary.Attribute(Nature.nz, 1024));
            }
        }
        DynamicCustomDictionary dictionary = new DynamicCustomDictionary(
                new DoubleArrayTrie<>(words), new BinTrie<>(), null);
        return segment.enableCustomDictionary(dictionary).enableCustomDictionaryForcing(true);
    }

    /**
     * 使用 HanLP 识别食物词的边界；只关联与业务字典全等的分词结果，连续未知片段保持原样。
     * 完整词项能查到字典时不再分词，避免将已有完整名称拆成更短的对象。
     *
     * @param body 已清洗的词项
     * @param index 当前有效字典名称索引
     * @param segment 本次导入独立的领域分词器
     * @return 顺序不变的已知名称及未匹配片段
     */
    private List<String> splitKnownFoods(String body, Map<String, List<CustomerDietOptionDto>> index,
                                        Segment segment) {
        if (body.isEmpty() || index.isEmpty() || !findOptions(body, index).isEmpty()) {
            return Collections.singletonList(body);
        }
        List<String> result = new ArrayList<>();
        int remainderStart = 0;
        for (Term term : segment.seg(body)) {
            if (findOptions(term.word, index).isEmpty()) {
                continue;
            }
            if (remainderStart < term.offset) {
                result.add(body.substring(remainderStart, term.offset));
            }
            result.add(term.word);
            remainderStart = term.offset + term.word.length();
        }
        if (remainderStart < body.length()) {
            result.add(body.substring(remainderStart));
        }
        return result;
    }

    /** 整词查不到时，去掉「所有的」「…的」「…也」这类不影响名称的修饰再查。 */
    private String refineLookup(String piece, Map<String, List<CustomerDietOptionDto>> index) {
        if (!findOptions(piece, index).isEmpty()) {
            return piece;
        }
        String refined = LOOKUP_NOISE_HEAD.matcher(piece).replaceFirst("").trim();
        refined = cleanBody(refined, index);
        return refined.isEmpty() ? piece : refined;
    }

    /** 正文本身是字典名称时原样返回，否则反复去掉尾部的「的/也/都/等」。 */
    private String cleanBody(String body, Map<String, List<CustomerDietOptionDto>> index) {
        String result = body;
        while (findOptions(result, index).isEmpty()) {
            String next = LOOKUP_NOISE_TAIL.matcher(result).replaceFirst("").trim();
            if (next.isEmpty() || next.equals(result)) {
                break;
            }
            result = next;
        }
        return result;
    }

    /**
     * 创建一个含稳定来源键、候选及自动录入对象的匹配项。
     *
     * @param row 来源行
     * @param sourceColumn Excel 列号
     * @param side D/F 方向
     * @param cellText 完整单元格原文
     * @param rawText 原词项
     * @param lookup 字典查找文本
     * @param termIndex 单元格内词项序号
     * @param candidates 名称完全匹配的全部候选
     * @return 匹配展示 DTO
     */
    private CustomerDietMatchDto createMatch(CustomerDietSourceRow row, int sourceColumn,
                                             String side, String cellText, String rawText, String lookup,
                                             int termIndex, List<CustomerDietOptionDto> candidates) {
        CustomerDietMatchDto match = new CustomerDietMatchDto();
        match.setSourceKey("DIET:" + row.getSourceRow() + ":" + sourceColumn + ":" + termIndex);
        match.setSourceRow(row.getSourceRow());
        match.setSourceColumn(sourceColumn);
        match.setSide(side);
        match.setRawText(rawText);
        match.setCellText(cellText);
        match.setLookupText(lookup);
        match.setCandidates(candidates.stream().map(this::copyOption).collect(Collectors.toList()));
        if (candidates.size() == 1) {
            match.setStatus("UNIQUE");
            match.setSelectedItems(Collections.singletonList(toItem(candidates.get(0))));
        } else if (candidates.size() > 1) {
            match.setStatus("MULTI");
            match.setSelectedItems(candidates.stream().map(this::toItem).collect(Collectors.toList()));
            match.setMessage("同名对象 " + candidates.size() + " 个，确认后将全部录入");
        } else {
            match.setStatus("UNMATCHED");
            match.setMessage("未匹配到字典对象，原文仍会保留");
        }
        return match;
    }

    /**
     * 为每个匹配项生成独立候选，避免 fastjson2 引用检测把重复字典对象输出为 $ref。
     *
     * @param source 字典候选
     * @return 字段完整且仅属于当前匹配项的候选
     */
    private CustomerDietOptionDto copyOption(CustomerDietOptionDto source) {
        CustomerDietOptionDto option = new CustomerDietOptionDto();
        option.setType(source.getType());
        option.setId(source.getId());
        option.setName(source.getName());
        option.setCategoryPath(source.getCategoryPath());
        return option;
    }

    /**
     * 按名称建立字典索引，每次导入只建一次，避免每个词项都全表扫描。
     *
     * @param options 当前有效字典选项
     * @return 规范化名称到同名候选列表（保持原顺序）的映射
     */
    private Map<String, List<CustomerDietOptionDto>> buildIndex(List<CustomerDietOptionDto> options) {
        Map<String, List<CustomerDietOptionDto>> index = new HashMap<>();
        for (CustomerDietOptionDto option : options) {
            if (option == null || option.getName() == null) {
                continue;
            }
            index.computeIfAbsent(canonical(option.getName()), key -> new ArrayList<>()).add(option);
        }
        return index;
    }

    /**
     * 按字典名称做全等匹配（仅忽略全半角、NBSP、零宽字符和首尾空白），不执行模糊或相似度搜索。
     *
     * @param exactName 查找文本
     * @param index 字典名称索引
     * @return 名称相同的所有候选
     */
    private List<CustomerDietOptionDto> findOptions(String exactName,
                                                    Map<String, List<CustomerDietOptionDto>> index) {
        List<CustomerDietOptionDto> found = index.get(canonical(exactName));
        return found == null ? Collections.<CustomerDietOptionDto>emptyList() : found;
    }

    /**
     * 检查来源医嘱/术后文本与已有客户共享字段是否冲突。
     *
     * @param candidate 已关联月份表客户的候选
     * @param data 本次第二工作表信息
     */
    private void mergeWithExisting(ImportCandidate candidate, CustomerDietImportData data) {
        ParsedCustomer parsed = candidate.getParsed();
        for (String issue : data.getIssues()) {
            parsed.addIssue(me.zhengjie.modules.customer.profile.domain.dto.CustomerImportIssueCategory.PROFILE_ERROR, issue);
        }
        if (data.getMedicalConflict() != null) {
            parsed.addIssue(me.zhengjie.modules.customer.profile.domain.dto.CustomerImportIssueCategory.PROFILE_ERROR,
                    data.getMedicalConflict());
        }
        if (data.getPostoperativeConflict() != null) {
            parsed.addIssue(me.zhengjie.modules.customer.profile.domain.dto.CustomerImportIssueCategory.PROFILE_ERROR,
                    data.getPostoperativeConflict());
        }
        if (data.getDealTimeConflict() != null) {
            parsed.addIssue(me.zhengjie.modules.customer.profile.domain.dto.CustomerImportIssueCategory.PROFILE_ERROR,
                    data.getDealTimeConflict());
        }
        if (candidate.getExistingProfile() != null) {
            String oldMedical = candidate.getExistingProfile().getMedicalRequirements();
            if (notBlank(oldMedical) && notBlank(data.getMedicalRequirements())
                    && !oldMedical.equals(data.getMedicalRequirements())) {
                parsed.addIssue(me.zhengjie.modules.customer.profile.domain.dto.CustomerImportIssueCategory.PROFILE_ERROR,
                        "已有客户医嘱与本次 E 列内容不同，未覆盖，请人工核对");
            }
            String oldPostoperative = candidate.getExistingProfile().getPostoperativeInfo();
            if (notBlank(oldPostoperative) && notBlank(data.getPostoperativeInfo())
                    && !oldPostoperative.equals(data.getPostoperativeInfo())) {
                parsed.addIssue(me.zhengjie.modules.customer.profile.domain.dto.CustomerImportIssueCategory.PROFILE_ERROR,
                        "已有客户术后信息与本次 H 列内容不同，未覆盖，请人工核对");
            }
        }
    }

    /**
     * 从字段去重集合中过滤掉空值。
     *
     * @param values 原始字段值集合
     * @return 非空值列表
     */
    private List<String> nonBlankValues(Set<String> values) {
        return values.stream().filter(this::notBlank).collect(Collectors.toList());
    }

    /**
     * 返回唯一的非空文本，多个不同值时返回 null 供冲突检查处理。
     *
     * @param values 原始字段值集合
     * @return 唯一非空值或 null
     */
    private String singleNonBlank(Set<String> values) {
        List<String> nonBlank = nonBlankValues(values);
        return nonBlank.size() == 1 ? nonBlank.get(0) : null;
    }

    /**
     * 按类型和ID向结构化字段追加对象并保持首次来源顺序。
     *
     * @param target 目标字段列表
     * @param item 待追加对象
     */
    private void appendUnique(List<CustomerDietItemDto> target, CustomerDietItemDto item) {
        String key = item.getType() + ":" + item.getId();
        boolean exists = target.stream().anyMatch(current -> key.equals(current.getType() + ":" + current.getId()));
        if (!exists) {
            target.add(toItem(item));
        }
    }

    /**
     * 去重追加完整原文块，不拆词、不改变标点或换行。
     *
     * @param target 原文块列表
     * @param text 来源单元格文本
     */
    private void appendUniqueText(List<String> target, String text) {
        if (isBlank(text) || target.contains(text)) {
            return;
        }
        target.add(text);
    }

    /**
     * 把字典候选转换为权威客户饮食对象引用。
     *
     * @param option 当前有效字典项
     * @return 仅含类型、ID和名称快照的引用
     */
    private CustomerDietItemDto toItem(CustomerDietOptionDto option) {
        CustomerDietItemDto item = new CustomerDietItemDto();
        item.setType(option.getType());
        item.setId(option.getId());
        item.setName(option.getName());
        return item;
    }

    /**
     * 复制已经解析的饮食对象引用。
     *
     * @param source 原引用
     * @return 规范引用副本
     */
    private CustomerDietItemDto toItem(CustomerDietItemDto source) {
        CustomerDietItemDto item = new CustomerDietItemDto();
        item.setType(source.getType());
        item.setId(source.getId());
        item.setName(source.getName());
        return item;
    }

    /**
     * 合并两段校验提示。
     *
     * @param existing 已有提示
     * @param value 新提示
     * @return 去空后的分号连接文本
     */
    private String join(String existing, String value) {
        if (isBlank(existing)) {
            return value;
        }
        return existing + "；" + value;
    }

    /** 判断字符串是否包含非空白字符。 */
    private boolean notBlank(String value) {
        return !isBlank(value);
    }

    /** 判断字符串是否为空或只含空白字符。 */
    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
