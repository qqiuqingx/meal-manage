package me.zhengjie.modules.customer.profile.service.impl;

import com.alibaba.fastjson2.JSON;
import me.zhengjie.modules.customer.profile.domain.CustomerProfile;
import me.zhengjie.modules.customer.profile.domain.dto.CustomerDietItemDto;
import me.zhengjie.modules.customer.profile.domain.dto.CustomerDietOptionDto;
import me.zhengjie.modules.customer.profile.mapper.CustomerProfileMapper;
import me.zhengjie.modules.customer.profile.service.CustomerDietMatchService;
import me.zhengjie.modules.customer.profile.util.CustomerDietRestrictionUtil;
import org.junit.jupiter.api.Test;
import java.util.Collections;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CustomerDietRestrictionWriterTest {
    @Test
    void autoSavesNewFoodThenHonorsRemovalAndRestartWithoutChangingRawText() {
        CustomerProfileMapper mapper = mock(CustomerProfileMapper.class);
        CustomerDietMatchService matcher = new CustomerDietMatchService();
        CustomerDietRestrictionWriter writer = new CustomerDietRestrictionWriter(mapper, matcher);
        CustomerProfile profile = new CustomerProfile();
        profile.setId(1L);
        profile.setDietaryRestrictionsRaw(Collections.singletonList("不吃秋葵\n少油"));
        when(mapper.selectByIdForInlineUpdate(1L)).thenReturn(profile);
        when(mapper.updateDietaryRestrictionsInline(eq(1L), anyString(), anyString(), eq("system:diet-rematch"), any()))
                .thenAnswer(invocation -> {
                    profile.setDietaryRestrictions(JSON.parseArray(invocation.getArgument(1), CustomerDietItemDto.class));
                    return 1;
                });
        CustomerDietOptionDto option = new CustomerDietOptionDto();
        option.setType("INGREDIENT"); option.setId(501L); option.setName("秋葵");

        assertTrue(writer.rematch(1L, matcher.snapshot(Collections.singletonList(option))));
        assertEquals("秋葵", profile.getDietaryRestrictions().get(0).getName());
        assertFalse(writer.rematch(1L, matcher.snapshot(Collections.singletonList(option))));
        CustomerDietRestrictionUtil.rememberManualSelection(profile, Collections.emptyList());
        assertFalse(writer.rematch(1L, matcher.snapshot(Collections.singletonList(option))));
        assertEquals(Collections.singletonList("INGREDIENT:501"), profile.getDietaryRestrictionExclusions());
        assertEquals(Collections.singletonList("不吃秋葵\n少油"), profile.getDietaryRestrictionsRaw());
        verify(mapper, times(1)).updateDietaryRestrictionsInline(eq(1L), anyString(), anyString(), anyString(), any());
    }
}
