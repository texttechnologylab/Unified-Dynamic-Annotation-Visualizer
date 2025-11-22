package org.texttechnologylab.udav.generators.settings;

import lombok.Getter;
import java.util.Set;
import java.util.stream.Collectors;


@Getter
public class FilterList<T> {

    public enum Mode { NO_FILTER_DEFINED, ONLY_WHITELIST_DEFINED, ONLY_BLACKLIST_DEFINED, BOTH_DEFINED }
    private final Mode mode;
    private final Class<T> type;

    private final String key;
    private final Set<T> whitelist;
    private final Set<T> blacklist;


    protected FilterList(Class<T> type, Mode mode, String key, Set<Object> whitelist, Set<Object> blacklist) {
        this.type = type;
        this.mode = mode;
        this.key = key;
        this.whitelist = whitelist == null? null : whitelist.stream().map(o -> (T) o).collect(Collectors.toUnmodifiableSet());
        this.blacklist = blacklist == null? null : blacklist.stream().map(o -> (T) o).collect(Collectors.toUnmodifiableSet());
    }
}
