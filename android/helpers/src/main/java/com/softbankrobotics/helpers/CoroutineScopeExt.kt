package com.softbankrobotics.helpers

import com.aldebaran.qi.Future
import com.aldebaran.qi.Promise
import kotlinx.coroutines.*
import java.util.concurrent.CancellationException

val SingleThreadedGlobalScope by lazy {
    CoroutineScope(newSingleThreadContext("DXLibrarySingleThread"))
}

//fun <T> CoroutineScope.asyncFuture(block: suspend () -> T): Future<T> {
fun <T> CoroutineScope.asyncFuture(block: suspend CoroutineScope.() -> T): Future<T> {
    val promise = Promise<T>()
    val deferred = async {
        try {
            promise.setValue(coroutineScope(block))
        }
        catch (e: CancellationException) {
            promise.setCancelled()
        }
        catch (e: Exception) {
            promise.setError(e.message)
        }
    }
    promise.setOnCancel {
        runBlocking {
            deferred.cancelAndJoin()
        }
    }
    return promise.future
}