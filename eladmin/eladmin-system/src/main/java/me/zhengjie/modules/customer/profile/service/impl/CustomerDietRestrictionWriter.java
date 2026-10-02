package me.zhengjie.modules.customer.profile.service.impl;

import com.alibaba.fastjson2.JSON;
import lombok.RequiredArgsConstructor;
import me.zhengjie.modules.customer.profile.domain.CustomerProfile;
import me.zhengjie.modules.customer.profile.domain.dto.CustomerDietItemDto;
import me.zhengjie.modules.customer.profile.mapper.CustomerProfileMapper;
import me.zhengjie.modules.customer.profile.service.CustomerDietMatchService;
import me.zhengjie.modules.customer.profile.util.CustomerDietRestrictionUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/** 在单客户独立事务内重新匹配、过滤人工排除并保存自动新增禁忌。 */
@Service
@RequiredArgsConstructor
public class CustomerDietRestrictionWriter {
    private final CustomerProfileMapper profileMapper;
    private final CustomerDietMatchService matcher;

    /**
     * 锁后读取最新原文与排除结果，不覆盖并发人工编辑；没有变化时不写更新时间。
     * @param customerId 本批次的客户主键
     * @param snapshot 本轮共享的字典和分词器
     * @return 是否实际新增了禁忌对象；客户不存在或没有变化时为 false
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public boolean rematch(Long customerId, CustomerDietMatchService.DictionarySnapshot snapshot) {
        CustomerProfile profile = profileMapper.selectByIdForInlineUpdate(customerId);
        if (profile == null) {
            return false;
        }
        List<CustomerDietItemDto> incoming = matcher.matchRestrictions(profile.getDietaryRestrictionsRaw(), snapshot);
        List<CustomerDietItemDto> merged = CustomerDietRestrictionUtil.mergeAutomatic(profile, incoming);
        List<CustomerDietItemDto> current = profile.getDietaryRestrictions() == null
                ? Collections.emptyList() : profile.getDietaryRestrictions();
        if (current.equals(merged)) {
            return false;
        }
        if (profileMapper.updateDietaryRestrictionsInline(customerId, JSON.toJSONString(merged),
                JSON.toJSONString(profile.getDietaryRestrictionExclusions()),
                "system:diet-rematch", LocalDateTime.now()) != 1) {
            throw new IllegalStateException("客户禁忌自动保存失败，客户ID=" + customerId);
        }
        return true;
    }
}
