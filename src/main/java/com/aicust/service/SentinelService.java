package com.aicust.service;

import com.alibaba.csp.sentinel.Entry;
import com.alibaba.csp.sentinel.SphU;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import org.springframework.stereotype.Component;

@Component
public class SentinelService {

    public boolean allow(String resource) {
        try (Entry entry = SphU.entry(resource)) {
            return true;
        } catch (BlockException e) {
            return false;
        }
    }
}
