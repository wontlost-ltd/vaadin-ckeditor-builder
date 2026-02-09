package com.wontlost.ckeditor.service;

import com.wontlost.ckeditor.domain.entity.Subscriber;
import com.wontlost.ckeditor.repository.oracle.OracleSubscriberRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Oracle 同步辅助 Bean
 * 独立 Bean 确保 @Transactional 代理正常拦截（避免同类内部调用绕过代理）
 */
@Component
public class OracleSyncHelper {

    @Autowired(required = false)
    private OracleSubscriberRepository oracleRepository;

    /**
     * 同步单个订阅者到 Oracle（有事务保护）
     */
    @Transactional(value = "oracleTransactionManager", timeout = 30)
    public void syncSubscriberToOracle(Subscriber subscriber) {
        if (oracleRepository == null) {
            return;
        }

        Optional<Subscriber> existing = oracleRepository.findByEmail(subscriber.getEmail());

        if (existing.isPresent()) {
            Subscriber oracleSubscriber = existing.get();
            SubscriberFieldMapper.updateFields(oracleSubscriber, subscriber);
            oracleRepository.save(oracleSubscriber);
        } else {
            Subscriber newSubscriber = SubscriberFieldMapper.copy(subscriber);
            newSubscriber.setId(null);
            oracleRepository.save(newSubscriber);
        }
    }

    /**
     * 恢复单条记录到 H2（独立事务，避免整批回滚）
     */
    @Transactional("h2TransactionManager")
    public void restoreSingleToH2(
            com.wontlost.ckeditor.repository.h2.H2SubscriberRepository h2Repository,
            Subscriber oracleSubscriber) {
        Optional<Subscriber> existing = h2Repository.findByEmail(oracleSubscriber.getEmail());

        if (existing.isPresent()) {
            Subscriber h2Subscriber = existing.get();
            SubscriberFieldMapper.updateFields(h2Subscriber, oracleSubscriber);
            h2Subscriber.markSyncSuccess();
            h2Repository.save(h2Subscriber);
        } else {
            Subscriber newSubscriber = SubscriberFieldMapper.copy(oracleSubscriber);
            newSubscriber.setId(null);
            newSubscriber.markSyncSuccess();
            h2Repository.save(newSubscriber);
        }
    }
}
