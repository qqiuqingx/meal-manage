package me.zhengjie.modules.meal.service.impl;

import com.hankcs.hanlp.seg.Segment;
import com.hankcs.hanlp.seg.common.Term;
import lombok.RequiredArgsConstructor;
import me.zhengjie.modules.meal.domain.DishIngredient;
import me.zhengjie.modules.meal.domain.dto.DishIngredientDto;
import me.zhengjie.modules.meal.domain.dto.DishIngredientQueryCriteria;
import me.zhengjie.modules.meal.service.DishIngredientService;
import me.zhengjie.modules.meal.util.DietDictionarySegmenter;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** 从制作流程精确识别已有启用配料的名称；不推断同义词、用量或烹饪语义。 */
@Service
@RequiredArgsConstructor
public class DishIngredientRecognitionService {

    private static final Pattern ZERO_WIDTH = Pattern.compile("[\\u200B-\\u200D\\u2060\\uFEFF\\uFE0F]");
    private final DishIngredientService ingredientService;

    /**
     * 使用本次启用配料字典识别全文，只读返回按首次出现顺序、配料ID去重的候选。
     * @param cookingMethod 制作流程；空、null或纯空白返回空列表
     * @return 配料字典ID、原名和单位；用量为null、备注为空，不持久化任何数据
     */
    public List<DishIngredientDto> recognize(String cookingMethod) {
        String text = normalize(cookingMethod);
        if (text.isEmpty()) {
            return Collections.emptyList();
        }
        DishIngredientQueryCriteria criteria = new DishIngredientQueryCriteria();
        criteria.setEnabled(true);
        Map<String, List<DishIngredient>> index = new LinkedHashMap<>();
        for (DishIngredient ingredient : ingredientService.queryAll(criteria)) {
            String name = normalize(ingredient.getName());
            if (ingredient.getId() != null && Boolean.TRUE.equals(ingredient.getEnabled()) && !name.isEmpty()) {
                index.computeIfAbsent(name, key -> new ArrayList<>()).add(ingredient);
            }
        }
        if (index.isEmpty()) {
            return Collections.emptyList();
        }
        Segment segment = DietDictionarySegmenter.create(index.keySet());
        Map<Integer, DishIngredientDto> matches = new LinkedHashMap<>();
        for (Term term : segment.seg(text)) {
            List<DishIngredient> ingredients = index.get(term.word);
            if (ingredients == null) {
                continue;
            }
            for (DishIngredient ingredient : ingredients) {
                if (!matches.containsKey(ingredient.getId())) {
                    DishIngredientDto dto = new DishIngredientDto();
                    dto.setIngredientId(ingredient.getId());
                    dto.setIngredientName(ingredient.getName());
                    dto.setUnit(ingredient.getUnit());
                    dto.setRemark("");
                    matches.put(ingredient.getId(), dto);
                }
            }
        }
        return new ArrayList<>(matches.values());
    }

    /** 对流程和名称同样规整全半角、零宽字符及首尾空白；返回值不写回字典。 */
    private String normalize(String text) {
        return text == null ? "" : ZERO_WIDTH.matcher(Normalizer.normalize(text, Normalizer.Form.NFKC))
                .replaceAll("").trim();
    }
}
