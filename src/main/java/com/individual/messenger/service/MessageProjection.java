package com.individual.messenger.service;
import com.individual.messenger.domain.Message;
/** Optional idempotent projections. Failures leave the message pending for retry. */
public interface MessageProjection { void publish(String type, Message message); }
