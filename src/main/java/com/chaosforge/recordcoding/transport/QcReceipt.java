package com.chaosforge.recordcoding.transport;

/** QC 写侧回执：对端记录 id 与接收时间。 */
public record QcReceipt(String recordId, String receivedAt) {}
