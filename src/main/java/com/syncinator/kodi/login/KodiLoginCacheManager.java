package com.syncinator.kodi.login;

import java.util.concurrent.TimeUnit;

import org.ehcache.Cache;
import org.ehcache.CacheManager;
import org.ehcache.config.builders.CacheConfigurationBuilder;
import org.ehcache.config.builders.CacheManagerBuilder;
import org.ehcache.config.builders.ResourcePoolsBuilder;
import org.ehcache.expiry.Duration;
import org.ehcache.expiry.Expirations;

public class KodiLoginCacheManager {
	
	private static final String PIN_ALIAS = "pin";
	
	private static CacheManager cacheManager = CacheManagerBuilder.newCacheManagerBuilder()
			.withCache(PIN_ALIAS, 
					CacheConfigurationBuilder
						.newCacheConfigurationBuilder(String.class, String.class, ResourcePoolsBuilder.heap(50000))
						.withExpiry(Expirations.timeToLiveExpiration(Duration.of(10, TimeUnit.MINUTES))))
			.build();
	
	static {
		cacheManager.init();
	}
	
	public static Cache<String, String> getPinCache(){
		return cacheManager.getCache(PIN_ALIAS, String.class, String.class);
	}
}
