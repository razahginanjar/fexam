package com.hand.demo.api.dto;

import lombok.Data;
import org.hzero.common.HZeroCacheKey;
import org.hzero.core.cache.CacheValue;
import org.hzero.core.cache.Cacheable;

@Data
public class UserDTO implements Cacheable {
    private Long userId;

    @CacheValue(key = HZeroCacheKey.USER,
            primaryKey = "userId",
            searchKey = "realName",
            structure = CacheValue.DataStructure.MAP_OBJECT)
    private String realName;
}
