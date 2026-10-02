package me.zhengjie.modules.customer.profile.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.zhengjie.modules.customer.profile.mapper.CustomerProfileMapper;
import me.zhengjie.modules.customer.profile.service.CustomerDietDictionaryService;
import me.zhengjie.modules.customer.profile.service.CustomerDietMatchService;
import org.springframework.stereotype.Service;
import java.util.List;

/** 按主键游标逐批重新匹配有原文的客户，一轮只建立一份字典快照。 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CustomerDietRematchServiceImpl {
    private static final int BATCH_SIZE = 100;
    private final CustomerProfileMapper profileMapper;
    private final CustomerDietDictionaryService dictionary;
    private final CustomerDietMatchService matcher;
    private final CustomerDietRestrictionWriter writer;

    /** 遍历当前客户原文并独立保存；单客户失败不影响其他客户，错误日志只记录主键。 */
    public void rematchAll() {
        CustomerDietMatchService.DictionarySnapshot snapshot = matcher.snapshot(dictionary.listActiveOptions());
        long afterId = 0;
        int changed = 0;
        int failed = 0;
        while (true) {
            List<Long> ids = profileMapper.findDietRematchIds(afterId, BATCH_SIZE);
            if (ids.isEmpty()) {
                break;
            }
            for (Long id : ids) {
                try {
                    if (writer.rematch(id, snapshot)) {
                        changed++;
                    }
                } catch (RuntimeException ex) {
                    failed++;
                    log.error("客户禁忌重新匹配失败，客户ID={}", id, ex);
                }
            }
            afterId = ids.get(ids.size() - 1);
        }
        log.info("客户禁忌自动匹配完成，更新客户={}，失败客户={}", changed, failed);
    }
}
