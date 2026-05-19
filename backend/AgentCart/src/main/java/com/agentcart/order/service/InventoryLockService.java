package com.agentcart.order.service;

import com.agentcart.exception.ErrorCode;
import com.agentcart.exception.OrderException;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Component
@RequiredArgsConstructor
public class InventoryLockService {

    private static final String LOCK_KEY_PREFIX = "inventory:product:";
    private static final long LOCK_WAIT_SECONDS = 5;

    private final RedissonClient redissonClient;

    public <T> T withLocks(List<Long> productIds, Supplier<T> action) {
        List<Long> sorted = productIds.stream().distinct().sorted().toList();
        List<RLock> acquired = new ArrayList<>();
        try {
            for (Long id : sorted) {
                RLock lock = redissonClient.getLock(LOCK_KEY_PREFIX + id);
                if (!lock.tryLock(LOCK_WAIT_SECONDS, TimeUnit.SECONDS)) {
                    throw new OrderException(ErrorCode.LOCK_ACQUISITION_FAILED);
                }
                acquired.add(lock);
            }
            return action.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new OrderException(ErrorCode.LOCK_ACQUISITION_FAILED);
        } finally {
            for (int i = acquired.size() - 1; i >= 0; i--) {
                RLock lock = acquired.get(i);
                if (lock.isHeldByCurrentThread()) {
                    lock.unlock();
                }
            }
        }
    }
}