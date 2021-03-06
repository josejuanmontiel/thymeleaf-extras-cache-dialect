package ch.mfrey.thymeleaf.extras.cache;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.thymeleaf.Arguments;
import org.thymeleaf.cache.ICache;
import org.thymeleaf.cache.StandardCache;
import org.thymeleaf.dom.Node;

public class CacheManager {
	private static final String CACHE_NAME = "CacheDialect";

	public static final CacheManager INSTANCE = new CacheManager();

	private static final Logger log = LoggerFactory.getLogger(CacheManager.class);

	private static String getCacheName(final String name, final String templateMode, final Locale locale) {
		return name + "_" + templateMode + "_" + locale;
	}

	private volatile ICache<String, List<Node>> cache;
	private volatile Map<String, List<Node>> cacheContainerReference;
	private volatile boolean cacheInitialized = false;

	public void evict(final Arguments arguments, final String cacheName) {
		evict(cacheName, arguments.getTemplateResolution().getTemplateMode(), arguments.getContext().getLocale());
	}

	public void evict(final String cacheName, final String templateMode, final Locale locale) {
		getCache().clearKey(getCacheName(cacheName, templateMode, locale));
	}

	public void evictByStartsWith(final String cacheName) {
		Map<String, List<Node>> cacheContainerReference = getCacheContainerReference();
		if (cacheContainerReference == null) {
			return;
		}
		for (String key : cacheContainerReference.keySet()) {
			if (key.startsWith(cacheName)) {
				getCache().clearKey(key);
			}
		}
	}

	public List<Node> get(final Arguments arguments, final String cacheName, final int cacheTTLs) {
		return get(cacheName, arguments.getTemplateResolution().getTemplateMode(), arguments.getContext().getLocale(),
				cacheTTLs);
	}

	public List<Node> get(final String cacheName, final String templateMode, final Locale locale, final int cacheTTLs) {
		if (cacheTTLs == 0) {
			return getCache().get(getCacheName(cacheName, templateMode, locale));
		} else {
			return getCache().get(getCacheName(cacheName, templateMode, locale), new TTLCacheValidityChecker(cacheTTLs));
		}
	}

	private final ICache<String, List<Node>> getCache() {
		if (!this.cacheInitialized) {
			synchronized (this) {
				if (!this.cacheInitialized) {
					initializeCache();
					this.cacheInitialized = true;
				}
			}
		}
		return this.cache;
	}

	private final Map<String, List<Node>> getCacheContainerReference() {
		if (!this.cacheInitialized) {
			synchronized (this) {
				if (!this.cacheInitialized) {
					initializeCache();
					this.cacheInitialized = true;
				}
			}
		}
		return this.cacheContainerReference;
	}

	private void initializeCache() {
		StandardCache<String, List<Node>> sc = new StandardCache<String, List<Node>>(CACHE_NAME, false, 10, 100, null, log);
		this.cache = sc;
		this.cacheContainerReference = initializeCacheContainerReference(sc);
	}

	@SuppressWarnings("unchecked")
	private Map<String, List<Node>> initializeCacheContainerReference(StandardCache<String, List<Node>> cache) {
		try {
			Field fieldCacheDataContainer = cache.getClass().getDeclaredField("dataContainer");
			fieldCacheDataContainer.setAccessible(true);
			Object cacheDataContainer = fieldCacheDataContainer.get(cache);
			Field fieldContainer = cacheDataContainer.getClass().getDeclaredField("container");
			fieldContainer.setAccessible(true);
			Object containerConcurrentMap = fieldContainer.get(cacheDataContainer);
			return (Map<String, List<Node>>) containerConcurrentMap;
		} catch (Exception e) {
			log.warn("Could not access ConcurrentHashMap of StandardCache. evictByStartsWith will not work!", e);
		}
		return null;
	}

	public void put(final Arguments arguments, final String cacheName, final List<Node> content) {
		put(cacheName, arguments.getTemplateResolution().getTemplateMode(), arguments.getContext().getLocale(), content);
	}

	public void put(final String cacheName, final String templateMode, final Locale locale, final List<Node> content) {
		getCache().put(getCacheName(cacheName, templateMode, locale), content);
	}

}
