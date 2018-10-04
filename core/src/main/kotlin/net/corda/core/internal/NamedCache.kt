package net.corda.core.internal

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.CacheLoader
import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.LoadingCache
import net.corda.core.DeleteForDJVM
import net.corda.core.KeepForDJVM

/**
 * Allow extra functionality to be injected to our caches.
 */
@KeepForDJVM
interface NamedCacheFactory {
    /**
     * Restrict the allowed characters of a cache name - this ensures that each cache has a name, and that
     * the name can be used to create a file name or a metric name.
     */
    fun checkCacheName(name: String) {
        require(!name.isBlank())
        require(allowedChars.matches(name))
    }

    @DeleteForDJVM
    fun <K, V> buildNamed(caffeine: Caffeine<in K, in V>, name: String): Cache<K, V>

    @DeleteForDJVM
    fun <K, V> buildNamed(caffeine: Caffeine<in K, in V>, name: String, loader: CacheLoader<K, V>): LoadingCache<K, V>
}

private val allowedChars = Regex("^[0-9A-Za-z_.]*\$")

class DefaultSizedCacheFactory(private val defaultSize: Long = 1024) : NamedCacheFactory {
    override fun <K, V> buildNamed(caffeine: Caffeine<in K, in V>, name: String): Cache<K, V> {
        checkCacheName(name)
        return caffeine.maximumSize(defaultSize).build<K, V>()
    }

    override fun <K, V> buildNamed(caffeine: Caffeine<in K, in V>, name: String, loader: CacheLoader<K, V>): LoadingCache<K, V> {
        checkCacheName(name)
        return caffeine.maximumSize(defaultSize).build<K, V>(loader)
    }
}