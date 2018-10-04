package net.corda.node.utilities

import com.codahale.metrics.MetricRegistry
import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.CacheLoader
import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.LoadingCache
import net.corda.core.internal.NamedCacheFactory
import net.corda.core.serialization.SerializeAsToken
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.node.services.config.NodeConfiguration
import java.util.concurrent.TimeUnit

/**
 * Allow passing metrics and config to caching implementations.
 */
interface BindableNamedCacheFactory : NamedCacheFactory, SerializeAsToken {
    /**
     * Build a new cache factory of the same type that incorporates metrics.
     */
    fun bindWithMetrics(metricRegistry: MetricRegistry): BindableNamedCacheFactory

    /**
     * Build a new cache factory of the same type that incorporates the associated configuration.
     */
    fun bindWithConfig(nodeConfiguration: NodeConfiguration): BindableNamedCacheFactory
}

class DefaultNamedCacheFactory private constructor(private val metricRegistry: MetricRegistry?, private val nodeConfiguration: NodeConfiguration?) : BindableNamedCacheFactory, SingletonSerializeAsToken() {
    constructor() : this(null, null)

    override fun bindWithMetrics(metricRegistry: MetricRegistry): BindableNamedCacheFactory = DefaultNamedCacheFactory(metricRegistry, this.nodeConfiguration)
    override fun bindWithConfig(nodeConfiguration: NodeConfiguration): BindableNamedCacheFactory = DefaultNamedCacheFactory(this.metricRegistry, nodeConfiguration)

    override fun <K, V> buildNamed(caffeine: Caffeine<in K, in V>, name: String): Cache<K, V> {
        checkCacheName(name)
        checkNotNull(metricRegistry)
        checkNotNull(nodeConfiguration)
        val configuredCaffeine = when {
            name.startsWith("RPCSecurityManagerShiroCache_") -> with(nodeConfiguration?.security?.authService?.options?.cache!!) { caffeine.maximumSize(this.maxEntries).expireAfterWrite(this.expireAfterSecs, TimeUnit.SECONDS) }
            name == "RPCServer_observableSubscription" -> caffeine
            name == "RpcClientProxyHandler_rpcObservable" -> caffeine
            name == "SerializationScheme_attachmentClassloader" -> caffeine
            name == "HibernateConfiguration_sessionFactories" -> caffeine.maximumSize(nodeConfiguration!!.database.mappedSchemaCacheSize)
            else -> throw IllegalArgumentException("Unexpected cache name $name. Did you add a new cache?")
        }
        return configuredCaffeine.build<K, V>()
    }

    override fun <K, V> buildNamed(caffeine: Caffeine<in K, in V>, name: String, loader: CacheLoader<K, V>): LoadingCache<K, V> {
        checkCacheName(name)
        checkNotNull(metricRegistry)
        checkNotNull(nodeConfiguration)
        val configuredCaffeine = with(nodeConfiguration!!) {
            when (name) {
                "DBTransactionStorage_transactions" -> caffeine.maximumWeight(this.transactionCacheSizeBytes)
                "NodeAttachmentService_attachmentContent" -> caffeine.maximumWeight(this.attachmentContentCacheSizeBytes)
                "NodeAttachmentService_attachmentPresence" -> caffeine.maximumSize(this.attachmentCacheBound)
                "PersistentIdentityService_partyByKey" -> caffeine.maximumSize(defaultCacheSize)
                "PersistentIdentityService_partyByName" -> caffeine.maximumSize(defaultCacheSize)
                "PersistentNetworkMap_nodesByKey" -> caffeine.maximumSize(defaultCacheSize)
                "PersistentNetworkMap_idByLegalName" -> caffeine.maximumSize(defaultCacheSize)
                "PersistentKeyManagementService_keys" -> caffeine.maximumSize(defaultCacheSize)
                "FlowDrainingMode_nodeProperties" -> caffeine.maximumSize(defaultCacheSize)
                "ContractUpgradeService_upgrades" -> caffeine.maximumSize(defaultCacheSize)
                "PersistentUniquenessProvider_transactions" -> caffeine.maximumSize(defaultCacheSize)
                "P2PMessageDeduplicator_processedMessages" -> caffeine.maximumSize(defaultCacheSize)
                "DeduplicationChecker_watermark" -> caffeine
                "BFTNonValidatingNotaryService_transactions" -> caffeine.maximumSize(defaultCacheSize)
                "RaftUniquenessProvider_transactions" -> caffeine.maximumSize(defaultCacheSize)
                else -> throw IllegalArgumentException("Unexpected cache name $name. Did you add a new cache?")
            }
        }
        return configuredCaffeine.build<K, V>(loader)
    }

    private val defaultCacheSize = 1024L
}