package me.zhengjie.modules.customer.profile.service;

import lombok.extern.slf4j.Slf4j;
import me.zhengjie.modules.customer.profile.service.impl.CustomerDietRematchServiceImpl;
import me.zhengjie.modules.meal.domain.event.DietDictionaryChangedEvent;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import java.util.concurrent.Executor;

/** 成功提交或应用就绪后启动重匹配，并将连续通知合并为串行后台任务。 */
@Component
@Slf4j
public class CustomerDietDictionaryChangeListener {
    private final CustomerDietRematchServiceImpl service;
    private final Executor executor;
    private final Object monitor = new Object();
    private boolean running;
    private long requestedVersion;

    /** 使用既有 taskAsync 线程池，不单独创建线程或改变全局线程池配置。 */
    public CustomerDietDictionaryChangeListener(CustomerDietRematchServiceImpl service,
                                               @Qualifier("taskAsync") Executor executor) {
        this.service = service;
        this.executor = executor;
    }

    /** 字典写事务成功后请求匹配；无事务或事务回滚时不会处理本事件。 */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDictionaryChanged(DietDictionaryChangedEvent event) {
        requestRematch();
    }

    /** 应用就绪时补齐已有原文，并幂等恢复停机期间未完成的匹配。 */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        requestRematch();
    }

    /** 合并待处理通知；任务提交到线程池之前释放锁，允许 CallerRunsPolicy 正常执行。 */
    private void requestRematch() {
        synchronized (monitor) {
            requestedVersion++;
            if (running) {
                return;
            }
            running = true;
        }
        try {
            executor.execute(this::drainRequests);
        } catch (RuntimeException ex) {
            synchronized (monitor) {
                running = false;
            }
            log.error("客户禁忌匹配任务提交失败，下次字典变化或应用启动将再次请求", ex);
        }
    }

    /** 消费最新通知版本；运行中发生变化时再执行一轮，收尾与新请求在同一锁下交接。 */
    private void drainRequests() {
        while (true) {
            long version;
            synchronized (monitor) {
                version = requestedVersion;
            }
            try {
                service.rematchAll();
            } catch (RuntimeException ex) {
                log.error("客户禁忌自动匹配批次失败", ex);
            }
            synchronized (monitor) {
                if (version == requestedVersion) {
                    running = false;
                    return;
                }
            }
        }
    }
}
