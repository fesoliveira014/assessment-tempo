## Code Review

You are reviewing the following code submitted as part of a task to implement an item cache in a highly concurrent application. The anticipated load includes: thousands of reads per second, hundreds of writes per second, tens of concurrent threads.
Your objective is to identify and explain the issues in the implementation that must be addressed before deploying the code to production. Please provide a clear explanation of each issue and its potential impact on production behaviour.

```kotlin
import java.util.concurrent.ConcurrentHashMap

class SimpleCache<K, V> {
    private val cache = ConcurrentHashMap<K, CacheEntry<V>>()
    private val ttlMs = 60000 // 1 minute
    
    data class CacheEntry<V>(val value: V, val timestamp: Long)
    
    fun put(key: K, value: V) {
        cache[key] = CacheEntry(value, System.currentTimeMillis())
    }
    
    fun get(key: K): V? {
        val entry = cache[key]
        if (entry != null) {
            if (System.currentTimeMillis() - entry.timestamp < ttlMs) {
                return entry.value
            }
        }
        return null
    }
    
    fun size(): Int {
        return cache.size
    }
}
```

1. TTL is only enforced on retrieval as expired entries are never removed from the cache. Dead entries pile up even under steady traffic, so memory grows until GC pauses get longer and the service eventually runs out of memory.

2. The cache has no size limit or eviction policy, so the number of live entries is only limited by traffic. Even if we fix #1, at hundreds of writes per second this is not a huge issue, but if we hit a large traffic spike or have a bot hit the cache with random keys, it can cause the service to run out of memory within the TTL.

3. An older entry can overwrite a newer one. The cache only offers `get` and `put`, so callers have to do `get -> miss -> load from DB -> put` themselves, and `put` always overwrites. Suppose two threads miss on key `k` and both load it:

   ```
   Thread 1: get(k) -> null -> reads DB -> gets v1
             // Thread 1 pauses because of GC or some other factor
             // DB row gets updated to v2 meanwhile
   Thread 2: get(k) -> null -> reads DB -> gets v2 -> put(k, v2)
   Thread 1: resumes -> put(k, v1)
   ```

   In this scenario we would be overwriting the cache with a stale value, and every read would return the stale value for the full TTL, since `put` gives it a fresh timestamp.

4. We can have a stampede issue where many threads try loading the same key at once. Similar to the previous issue, if we have a large number of threads trying to get the value for key `k` all at once and `k` is not in the cache or has expired, all concurrent threads will miss until the first thread finishes loading and puts the value in the cache. In this case we can see several issues happening:
    - The DB is hit with the same request dozens of times, increasing its load
    - The thread pool fills as it takes longer for the threads to complete their jobs, causing unrelated requests to queue behind them and latency spikes
    - It will happen again and again as the TTL expires

5. `System.currentTimeMillis()` reads the wall clock, which can jump: an NTP correction, a manual time change, or a VM being paused or migrated can move it backwards or forwards. If it jumps back, `now - timestamp` goes negative and entries stay valid for as long as the jump, so we serve stale data. If it jumps forward by more than a minute, every entry expires at once, causing a stampede across all keys. Elapsed time should be measured with `System.nanoTime()`, which only moves forward.

6. Depending on the purpose of the cache, a `get(k)` returning null could be a valid value, but we have no way to discern it from a cache miss. In this case we could be putting extra pressure onto the DB by reloading the null value on every request. Similarly, `put(null, v)` compiles, because `K` allows nullable types, but throws `NullPointerException` at runtime since `ConcurrentHashMap` rejects null keys. Declaring `SimpleCache<K : Any, V : Any>` fixes both.

7. We have no way to invalidate entries or the entire cache. When we write to the DB through requests or async jobs, we have no way to tell the cache to drop values for a given key, so that the next request reloads the value. The cache relies only on the TTL to determine if an entry is still valid, so after a DB update readers keep getting the old value for up to a minute (longer with #5).

8. `size()` is wrong since it counts expired entries that we can never `get`, and because of #1 it only ever grows, so any metric or alert based on it is misleading.

9. There is no observability or hit/miss metrics, so we have no way to measure the efficacy of the cache.