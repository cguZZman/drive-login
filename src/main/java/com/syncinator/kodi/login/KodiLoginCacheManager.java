package com.syncinator.kodi.login;

import java.time.Duration;

import org.ehcache.Cache;
import org.ehcache.CacheManager;
import org.ehcache.config.builders.CacheConfigurationBuilder;
import org.ehcache.config.builders.CacheManagerBuilder;
import org.ehcache.config.builders.ExpiryPolicyBuilder;
import org.ehcache.config.builders.ResourcePoolsBuilder;

import com.syncinator.kodi.login.model.Pin;

public class KodiLoginCacheManager {
	
	private static final String PIN_ALIAS = "pin";
	
	private static CacheManager pinCacheManager = CacheManagerBuilder.newCacheManagerBuilder()
			.withCache(PIN_ALIAS, 
					CacheConfigurationBuilder
						.newCacheConfigurationBuilder(String.class, Pin.class, ResourcePoolsBuilder.heap(100000))
						.withExpiry(ExpiryPolicyBuilder.timeToLiveExpiration(Duration.ofMinutes(3))))
			.build();
	
	static {
		pinCacheManager.init();
	}
	
	public static Cache<String, Pin> getPinCache(){
		return pinCacheManager.getCache(PIN_ALIAS, String.class, Pin.class);
	}
}
