// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.subscription;

/**
 * Thrown when {@link ConfigSubscriber} is closed
 *
 * @author bjorncs
 * @deprecated  Will be removed in Vespa 8. Only for internal use.
 */
@Deprecated(forRemoval = true, since = "7")
public class SubscriberClosedException extends RuntimeException {}
