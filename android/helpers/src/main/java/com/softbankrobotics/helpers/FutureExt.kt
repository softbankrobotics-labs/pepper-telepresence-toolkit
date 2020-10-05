package com.softbankrobotics.helpers

import com.aldebaran.qi.Future
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.cancelFutureOnCancellation
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.CancellationException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Suspend a coroutine until the Future has either finished with a value, or has been cancelled or
 * has failed.
 * @return The future value in case of success.
 *   Or throw CancellationException in case Future was cancelled.
 *   Or throw ExecutionException in case Future terminated with an error.
 */
suspend fun <T> Future<T>?.await(): T =
    suspendCancellableCoroutine { cont: CancellableContinuation<T> ->
        this?.let { future ->
            cont.cancelFutureOnCancellation(future)
            future.thenConsume {
                when {
                    it.isSuccess -> cont.resume(it.value)
                    it.isCancelled -> cont.resumeWithException(CancellationException())
                    else -> cont.resumeWithException(it.error)
                }
            }
        }
    }

/**
 * Suspend a coroutine until the Future has either finished with a value, or has been cancelled or
 * has failed.
 * @return The future value in case of success or null if the future was cancelled or failed.
 */
suspend fun <T> Future<T>?.awaitOrNull(): T? =
    suspendCancellableCoroutine { cont: CancellableContinuation<T?> ->
        this?.let { future ->
            cont.cancelFutureOnCancellation(future)
            future.thenConsume {
                when {
                    it.isSuccess -> cont.resume(it.value)
                    it.isCancelled -> cont.resume(null)
                    else -> cont.resume(null)
                }
            }
        }
    }