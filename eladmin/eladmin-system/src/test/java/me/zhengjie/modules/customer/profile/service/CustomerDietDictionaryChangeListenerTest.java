package me.zhengjie.modules.customer.profile.service;

import me.zhengjie.modules.customer.profile.service.impl.CustomerDietRematchServiceImpl;
import me.zhengjie.modules.meal.domain.event.DietDictionaryChangedEvent;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.TransactionDefinition;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.Executor;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CustomerDietDictionaryChangeListenerTest {
    /** 无数据库的事务管理器，仅测试 Spring 提交/回滚事件语义。 */
    static class TestTransactions extends AbstractPlatformTransactionManager {
        @Override protected Object doGetTransaction() { return new Object(); }
        @Override protected void doBegin(Object transaction, TransactionDefinition definition) { }
        @Override protected void doCommit(DefaultTransactionStatus status) { }
        @Override protected void doRollback(DefaultTransactionStatus status) { }
    }

    static class QueuedExecutor implements Executor {
        final Queue<Runnable> tasks = new ArrayDeque<>();
        @Override public void execute(Runnable task) { tasks.add(task); }
    }

    @Configuration
    @EnableTransactionManagement
    static class Config {
        @Bean TestTransactions transactionManager() { return new TestTransactions(); }
        @Bean CustomerDietRematchServiceImpl service() { return mock(CustomerDietRematchServiceImpl.class); }
        @Bean QueuedExecutor taskAsync() { return new QueuedExecutor(); }
        @Bean CustomerDietDictionaryChangeListener listener() {
            return new CustomerDietDictionaryChangeListener(service(), taskAsync());
        }
    }

    @Test
    void onlyCommittedDictionaryChangesScheduleMatching() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(Config.class)) {
            QueuedExecutor executor = context.getBean(QueuedExecutor.class);
            TransactionTemplate transaction = new TransactionTemplate(context.getBean(TestTransactions.class));
            context.publishEvent(new DietDictionaryChangedEvent());
            assertTrue(executor.tasks.isEmpty());
            transaction.execute(status -> {
                context.publishEvent(new DietDictionaryChangedEvent());
                assertTrue(executor.tasks.isEmpty());
                status.setRollbackOnly();
                return null;
            });
            assertTrue(executor.tasks.isEmpty());
            transaction.execute(status -> {
                context.publishEvent(new DietDictionaryChangedEvent());
                assertTrue(executor.tasks.isEmpty());
                return null;
            });
            assertEquals(1, executor.tasks.size());
            executor.tasks.remove().run();
            verify(context.getBean(CustomerDietRematchServiceImpl.class)).rematchAll();
        }
    }

    @Test
    void coalescesQueuedEventsAndReplaysChangesArrivingDuringWork() {
        QueuedExecutor executor = new QueuedExecutor();
        CustomerDietRematchServiceImpl service = mock(CustomerDietRematchServiceImpl.class);
        CustomerDietDictionaryChangeListener listener = new CustomerDietDictionaryChangeListener(service, executor);
        listener.onApplicationReady();
        listener.onDictionaryChanged(new DietDictionaryChangedEvent());
        listener.onDictionaryChanged(new DietDictionaryChangedEvent());
        assertEquals(1, executor.tasks.size());
        doAnswer(invocation -> {
            listener.onDictionaryChanged(new DietDictionaryChangedEvent());
            return null;
        }).doNothing().when(service).rematchAll();
        executor.tasks.remove().run();
        verify(service, times(2)).rematchAll();
        listener.onDictionaryChanged(new DietDictionaryChangedEvent());
        assertEquals(1, executor.tasks.size());
    }

    @Test
    void retriesOnNextEventAfterTaskSubmissionOrBatchFailure() {
        CustomerDietRematchServiceImpl service = mock(CustomerDietRematchServiceImpl.class);
        Executor executor = mock(Executor.class);
        doThrow(new java.util.concurrent.RejectedExecutionException()).doAnswer(invocation -> {
            ((Runnable) invocation.getArgument(0)).run();
            return null;
        }).when(executor).execute(any(Runnable.class));
        CustomerDietDictionaryChangeListener listener = new CustomerDietDictionaryChangeListener(service, executor);
        listener.onApplicationReady();
        verifyNoInteractions(service);
        doThrow(new IllegalStateException("batch failure")).doNothing().when(service).rematchAll();
        listener.onDictionaryChanged(new DietDictionaryChangedEvent());
        listener.onDictionaryChanged(new DietDictionaryChangedEvent());
        verify(service, times(2)).rematchAll();
    }
}
