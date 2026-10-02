package me.zhengjie.modules.customer.profile.service;

import com.alibaba.fastjson2.JSON;
import me.zhengjie.modules.customer.profile.domain.CustomerProfile;
import me.zhengjie.modules.customer.profile.domain.dto.CustomerDietItemDto;
import me.zhengjie.modules.customer.profile.util.CustomerDietRestrictionUtil;
import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.Collections;
import static org.junit.jupiter.api.Assertions.*;

class CustomerDietRestrictionTest {
    @Test
    void remembersRemovalAcrossAutomaticRematchesAndAllowsExplicitRestore() {
        CustomerProfile profile = new CustomerProfile();
        CustomerDietItemDto ingredient = item("INGREDIENT", 501L);
        CustomerDietItemDto tag = item("INGREDIENT_TAG", 501L);
        profile.setDietaryRestrictions(Arrays.asList(ingredient, tag));
        CustomerDietRestrictionUtil.rememberManualSelection(profile, Collections.singletonList(tag));
        assertEquals(Collections.singletonList("INGREDIENT:501"), profile.getDietaryRestrictionExclusions());
        assertEquals(Collections.singletonList(tag), CustomerDietRestrictionUtil.mergeAutomatic(profile,
                Arrays.asList(ingredient, tag)));

        CustomerDietRestrictionUtil.rememberManualSelection(profile, Arrays.asList(tag, ingredient));
        assertTrue(profile.getDietaryRestrictionExclusions().isEmpty());
        assertEquals(Arrays.asList(tag, ingredient), CustomerDietRestrictionUtil.mergeAutomatic(profile,
                Collections.singletonList(ingredient)));
    }

    @Test
    void preservesManualItemsHistoricalNamesAndPreviouslyExcludedKeys() {
        CustomerProfile profile = new CustomerProfile();
        CustomerDietItemDto manual = item("DISH", 10L);
        profile.setDietaryRestrictions(Collections.singletonList(manual));
        profile.setDietaryRestrictionExclusions(Collections.singletonList("INGREDIENT:501"));
        CustomerDietItemDto renamed = item("DISH", 10L);
        renamed.setName("新名称");
        assertEquals(Collections.singletonList(manual), CustomerDietRestrictionUtil.mergeAutomatic(profile,
                Arrays.asList(renamed, item("INGREDIENT", 501L))));
        assertEquals("名称", profile.getDietaryRestrictions().get(0).getName());
        CustomerDietRestrictionUtil.rememberManualSelection(profile, Collections.emptyList());
        assertEquals(Arrays.asList("INGREDIENT:501", "DISH:10"), profile.getDietaryRestrictionExclusions());
    }

    @Test
    void keepsExclusionsPrivateInJsonInputAndOutput() {
        CustomerProfile profile = new CustomerProfile();
        profile.setDietaryRestrictionExclusions(Collections.singletonList("INGREDIENT:501"));
        assertFalse(JSON.toJSONString(profile).contains("dietaryRestrictionExclusions"));
        CustomerProfile parsed = JSON.parseObject("{\"dietaryRestrictionExclusions\":[\"DISH:1\"]}", CustomerProfile.class);
        assertNull(parsed.getDietaryRestrictionExclusions());
    }

    private CustomerDietItemDto item(String type, Long id) {
        CustomerDietItemDto result = new CustomerDietItemDto();
        result.setType(type);
        result.setId(id);
        result.setName("名称");
        return result;
    }
}
