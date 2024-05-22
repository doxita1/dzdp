package com.dzdp.utils;

public interface ILock {
    //尝试获取锁
    boolean TryLock(long timeSec);
    //释放锁
    void unlock();
}
