package com.chaosforge.recordcoding.coding;

/** 可插拔字典接口：本地文件 / 在线查询 / 不可用三态共用。 */
public interface CodeDictionary {
    DictionaryResult lookup(String code);
    default DictionaryResult exists(String code) { return lookup(code); }
}
