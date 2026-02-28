package org.texttechnologylab.udav.generators.settings;

import org.texttechnologylab.udav.generators.Generator;
import org.texttechnologylab.udav.pipeline.JSONView;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

// TODO: Make sub maps also a full part of this (Unicase, FilterLists)


public class GeneratorSettings {

    // Constants that apply to all settings
    public final static boolean CASE_SENSITIVE_KEYS = false;
    public final static boolean CASE_SENSITIVE_VALUES = true;

    // Constants that apply to all non-FilterList settings
    public final static boolean EXPLICIT_NULL_SETTINGS_IN_GENERATOR_OVERWRITE_GROUP_SETTINGS = true;
    public final static boolean EXPLICIT_NULL_SETTINGS_IN_GROUP_OVERWRITE_GENERATOR_SETTINGS = false;

    // --- JSON FilterList (Whitelist/Blacklist) Settings Constants ---
    // Make sure that the same string doesn't exist in multiple suffix-arrays.
    public final static String[] WHITELIST_SUFFIX = {"Whitelist"};
    public final static String[] BLACKLIST_SUFFIX = {"Blacklist"};
    public final static String[] PRIORITY_WHITELIST_SUFFIX = {"WhitelistPriority"};
    public final static Map<String, Boolean> FILTERLIST_CASE_SENSITIVE_VALUES = Map.of("files", true, "categories", false);
    public final static boolean FILTERLIST_CASE_SENSITIVE_VALUES_DEFAULT = false;
    public final static Class<?>[] FILTERLIST_KNOWN_TYPES = {String.class, Integer.class, Double.class};
    public final static boolean EXPLICIT_NULL_FILTERLIST_SETTINGS_IN_GENERATOR_OVERWRITE_GROUP_SETTINGS = true;
    public final static boolean EXPLICIT_NULL_FILTERLIST_SETTINGS_IN_GROUP_OVERWRITE_GENERATOR_SETTINGS = false;


    private final static String[] finalWhitelistSuffix;
    private final static String[] finalBlacklistSuffix;
    private final static String[] finalPriorityWhitelistSuffix;
    private final static Map<String, Boolean> finalFilterListsCaseSensitiveValues;

    static {
        if (CASE_SENSITIVE_KEYS) {
            finalWhitelistSuffix = WHITELIST_SUFFIX;
            finalBlacklistSuffix = BLACKLIST_SUFFIX;
            finalPriorityWhitelistSuffix = PRIORITY_WHITELIST_SUFFIX;
            finalFilterListsCaseSensitiveValues = FILTERLIST_CASE_SENSITIVE_VALUES;
        } else {
            finalWhitelistSuffix = Arrays.stream(WHITELIST_SUFFIX).map(GeneratorSettings::unicaseKey).toArray(String[]::new);
            finalBlacklistSuffix = Arrays.stream(BLACKLIST_SUFFIX).map(GeneratorSettings::unicaseKey).toArray(String[]::new);
            finalPriorityWhitelistSuffix = Arrays.stream(PRIORITY_WHITELIST_SUFFIX).map(GeneratorSettings::unicaseKey).toArray(String[]::new);
            finalFilterListsCaseSensitiveValues = FILTERLIST_CASE_SENSITIVE_VALUES.entrySet().stream()
                    .collect(Collectors.toMap(e -> unicaseKey(e.getKey()), Map.Entry::getValue, (v1, v2) -> v2));
        }
    }


    private Map<String, Object> settingsMap;
    private Map<String, Set<Object>> whitelists;
    private Map<String, Set<Object>> blacklists;
    private Map<String, Set<Object>> priorityWhitelists;


    private GeneratorSettings() {
        settingsMap = new HashMap<>();
        whitelists = new HashMap<>();
        blacklists = new HashMap<>();
        priorityWhitelists = new HashMap<>();
    }

    public String getStringSettingOrDefault(String settingKey, String defaultValue) {
        String s = getStringSetting(settingKey);
        return (s == null) ? defaultValue : s;
    }
    public boolean getBooleanSettingOrDefault(String settingKey, boolean defaultValue) {
        Boolean b = getBooleanSetting(settingKey);
        return (b == null) ? defaultValue : b;
    }
    public Map<?, ?> getMapSettingOrDefault(String settingKey, Map<?, ?> defaultValue) {
        Map<?, ?> m = getMapSetting(settingKey);
        return (m == null) ? defaultValue : m;
    }
    public String getStringSetting(String settingKey) {
        if (!CASE_SENSITIVE_KEYS) settingKey = unicaseKey(settingKey);
        Object o = settingsMap.get(settingKey);
        if (o == null) return null;
        if (o.getClass() != String.class) return null;
        return (String) o;
    }
    public Boolean getBooleanSetting(String settingKey) {
        if (!CASE_SENSITIVE_KEYS) settingKey = unicaseKey(settingKey);
        Object o = settingsMap.get(settingKey);
        if (o == null) return null;
        if (o.getClass() != Boolean.class) return null;
        return (Boolean) o;
    }
    public Map<?, ?> getMapSetting(String settingKey) {
        if (!CASE_SENSITIVE_KEYS) settingKey = unicaseKey(settingKey);
        Object o = settingsMap.get(settingKey);
        if (o == null) return null;
        if (!(o instanceof Map<?,?>)) return null;
        return (Map<?, ?>) o;
    }

    public FilterList<String> generateStringFilterList(String key) {
        FilterList<?> filterList = generateFilterList(key, String.class);
        if (!String.class.equals(filterList.getType())) throw new IllegalArgumentException("FilterList for key \"" + key + "\" is not a FilterList of type String.");
        return (FilterList<String>) filterList;
    }

    public FilterList<?> generateFilterList(String key) {
        return generateFilterList(key, Object.class);
    }

    public FilterList<?> generateFilterList(String key, Class<?> wantedType) {
        if (!CASE_SENSITIVE_KEYS) key = unicaseKey(key);
        Set<Object> whitelist = whitelists.get(key);
        if (whitelist != null) whitelist = new HashSet<>(whitelist);
        Set<Object> blacklist = blacklists.get(key);
        if (blacklist != null) blacklist = new HashSet<>(blacklist);
        Set<Object> priorityWhitelist = priorityWhitelists.get(key);

        FilterList.Mode mode;
        Set<Object> allElements = null;

        // Determine whitelist/blacklist mode
        if (whitelist == null && blacklist == null) {
            mode = FilterList.Mode.NO_FILTER_DEFINED;
        } else if (whitelist != null && blacklist == null) {
            mode = FilterList.Mode.ONLY_WHITELIST_DEFINED;
            if (priorityWhitelist != null) whitelist.addAll(priorityWhitelist);
            allElements = whitelist;
        } else if (whitelist == null) {
            mode = FilterList.Mode.ONLY_BLACKLIST_DEFINED;
            if (priorityWhitelist != null) blacklist.removeAll(priorityWhitelist);
            allElements = blacklist;
        } else {
            mode = FilterList.Mode.BOTH_DEFINED;
            if (priorityWhitelist != null) blacklist.removeAll(priorityWhitelist);
            whitelist.removeAll(blacklist);
            allElements = Stream.concat(whitelist.stream(), blacklist.stream()).collect(Collectors.toSet());
        }

        // Determine class of FilterList
        Class<?> filterListType = null;
        if (allElements != null) {
            outer:
            for (Object e : allElements) {
                for (Class<?> typeClass : FILTERLIST_KNOWN_TYPES) {
                    if (typeClass.isInstance(e)) {
                        if (filterListType == null) {
                            filterListType = typeClass;
                            continue outer;
                        } else if (filterListType != typeClass) {
                            filterListType = Object.class;
                            break outer;
                        }
                    }
                }
            }
        }
        if (filterListType == null) filterListType = wantedType;

        /* if (filterListType == String.class) {
            return new FilterList<>(filterListType, mode, key, whitelist, blacklist);
        } else if (filterListType == Integer.class) {
            return new FilterList<>(filterListType, mode, key, whitelist, blacklist);
        } else if (filterListType == Double.class) {
            return new FilterList<>(filterListType, mode, key, whitelist, blacklist);
        } */
        return new FilterList<>(filterListType, mode, key, whitelist, blacklist);
    }


    public void defineFilterListUniversalSet(String key, Set<Object> universalSet) {
        if (!CASE_SENSITIVE_KEYS) key = unicaseKey(key);
        Boolean caseSensitiveValues = finalFilterListsCaseSensitiveValues.get(key);
        if (caseSensitiveValues == null) caseSensitiveValues = FILTERLIST_CASE_SENSITIVE_VALUES_DEFAULT;
        Collection<Object> universal = caseSensitiveValues? universalSet : makeStringValuesUnicase(universalSet);
        Set<Object> w = whitelists.get(key);
        if (w == null) whitelists.put(key, new HashSet<>(universal)); else w.retainAll(universal);
    }
    public void defineFilterListUniversalSetString(String key, Set<String> universalSet) {
        defineFilterListUniversalSet(key, new HashSet<>(universalSet));
    }

    private static String unicaseKey(String key) {
        return key.toUpperCase();
    }
    private static String unicaseValue(String value) {
        return value.toUpperCase();
    }

    private static JSONView getJSONViewOptionalSubMap(JSONView view, String name) {
        JSONView outputView = null;
        try { outputView = view.get(name);
        } catch (Exception ignored) {}
        if (outputView != null && !outputView.isMap()) outputView = null;
        return outputView;
    }

    // boolean -> String und list object
    private static Object[] checkSettingIsCustomList(String settingKey, String[] customListSuffix) {
        if (settingKey == null || customListSuffix == null) return null;
        String checkKey = CASE_SENSITIVE_KEYS? settingKey : unicaseKey(settingKey);
        for (String suffix : customListSuffix) {
            if (checkKey.endsWith(suffix)) {
                String keyWithoutSuffix = settingKey.substring(0, settingKey.length() - suffix.length());
                return new Object[]{keyWithoutSuffix, customListSuffix};
            }
        }
        return null;
    }

    private static Object[] checkSettingsIsAnyCustomList(String settingKey) {
        Object[] shortKeyAndCustomList = checkSettingIsCustomList(settingKey, finalWhitelistSuffix);
        if (shortKeyAndCustomList != null) return shortKeyAndCustomList;
        shortKeyAndCustomList = checkSettingIsCustomList(settingKey, finalBlacklistSuffix);
        if (shortKeyAndCustomList != null) return shortKeyAndCustomList;
        shortKeyAndCustomList = checkSettingIsCustomList(settingKey, finalPriorityWhitelistSuffix);
        return shortKeyAndCustomList;
    }

    private static List<Object> makeStringValuesUnicase(Collection<Object> collection) {
        if (collection == null) return null;
        return collection.stream()
                .map(item -> (item instanceof String s) ? unicaseValue(s) : item)
                .collect(Collectors.toList());
    }
    private static Object makeStringValuesUnicase(Object object) {
        if (object instanceof String) return unicaseValue((String) object);
        if (object instanceof Collection<?>) return makeStringValuesUnicase(object);
        return object;
    }

    public static GeneratorSettings fromConfig(JSONView config) {
        GeneratorSettings settings = new GeneratorSettings();
        JSONView settingsView;
        try { settingsView = config.get("settings"); }
        catch (Exception ignored) { return settings; }
        if (!settingsView.isMap()) { return settings; }
        Map<String, Object> tempSettingsMap = settingsView.asMap();

        for (Map.Entry<String, Object> entry : tempSettingsMap.entrySet()) {
            Object[] shortKeyAndCustomList = checkSettingsIsAnyCustomList(entry.getKey());
            if (shortKeyAndCustomList == null) {
                String key = CASE_SENSITIVE_KEYS ? entry.getKey() : unicaseKey(entry.getKey());
                Object value = CASE_SENSITIVE_VALUES ? entry.getValue() : makeStringValuesUnicase(entry.getValue());
                settings.settingsMap.put(key, value);
            } else {
                List<Object> list;
                String shortKey = (String) shortKeyAndCustomList[0];
                shortKey = CASE_SENSITIVE_KEYS ? shortKey : unicaseKey(shortKey);
                HashSet<Object> finalSet;
                if (entry.getValue() == null) { finalSet = null;
                } else {
                    try { list = (List<Object>) entry.getValue();
                    } catch (Exception ignored) { list = List.of(entry.getValue()); /* Make "EXAMPLE" into ["EXAMPLE"] list */ }
                    Boolean caseSensitiveValues = finalFilterListsCaseSensitiveValues.get(shortKey);
                    if (caseSensitiveValues == null) caseSensitiveValues = FILTERLIST_CASE_SENSITIVE_VALUES_DEFAULT;
                    if (!caseSensitiveValues) list = makeStringValuesUnicase(list);
                    finalSet = new HashSet<>(list);
                }
                String[] customList = (String[]) shortKeyAndCustomList[1];
                if (customList == finalWhitelistSuffix) {
                    settings.whitelists.put(shortKey, finalSet);
                } else if (customList == finalBlacklistSuffix) {
                    settings.blacklists.put(shortKey, finalSet);
                } else {
                    settings.priorityWhitelists.put(shortKey, finalSet);
                }
            }
        }
        return settings;
    }

    public static GeneratorSettings fromGenerator(Generator generator) {
        GeneratorSettings settings = new GeneratorSettings();

        Map<String, NonNumericScalarCombinationMode> nonNumericScalarModes = generator.preSetup_defineAllNonNumericScalarSettingsCombinationMode();
        Map<String, NumericScalarCombinationMode> numericScalarModes = generator.preSetup_defineAllNumericScalarSettingsCombinationMode();
        Map<String, ListCombinationMode> listModes = generator.preSetup_defineAllListSettingsCombinationMode();
        Map<String, MapCombinationMode> mapModes = generator.preSetup_defineAllMapSettingsCombinationMode();
        Map<String, FilterListCombinationMode> filterListModes = generator.preSetup_defineAllFilterListSettingsCombinationMode();
        Map<String, WhiteBlackListCombinationMode> whitelistModes = generator.preSetup_defineAllWhitelistSettingsCombinationMode();
        Map<String, WhiteBlackListCombinationMode> blacklistModes = generator.preSetup_defineAllBlacklistSettingsCombinationMode();
        if (!CASE_SENSITIVE_KEYS) {
            // Make keys unicase
            nonNumericScalarModes = nonNumericScalarModes.entrySet().stream()
                    .collect(Collectors.toMap(e -> unicaseKey(e.getKey()), Map.Entry::getValue,(v1, v2) -> v1, HashMap::new));
            numericScalarModes = numericScalarModes.entrySet().stream()
                    .collect(Collectors.toMap(e -> unicaseKey(e.getKey()), Map.Entry::getValue,(v1, v2) -> v1, HashMap::new));
            listModes = listModes.entrySet().stream()
                    .collect(Collectors.toMap(e -> unicaseKey(e.getKey()), Map.Entry::getValue,(v1, v2) -> v1, HashMap::new));
            mapModes = mapModes.entrySet().stream()
                    .collect(Collectors.toMap(e -> unicaseKey(e.getKey()), Map.Entry::getValue,(v1, v2) -> v1, HashMap::new));
            filterListModes = filterListModes.entrySet().stream()
                    .collect(Collectors.toMap(e -> unicaseKey(e.getKey()), Map.Entry::getValue,(v1, v2) -> v1, HashMap::new));
            whitelistModes = whitelistModes.entrySet().stream()
                    .collect(Collectors.toMap(e -> unicaseKey(e.getKey()), Map.Entry::getValue,(v1, v2) -> v1, HashMap::new));
            blacklistModes = blacklistModes.entrySet().stream()
                    .collect(Collectors.toMap(e -> unicaseKey(e.getKey()), Map.Entry::getValue,(v1, v2) -> v1, HashMap::new));
        }

        GeneratorSettings settingsBundle = generator.getSettingsBundle();
        GeneratorSettings settingsGenerator = generator.getSettingsGenerator();

        Map<String, Object> settingsMapBundle = settingsBundle.settingsMap;
        Map<String, Object> settingsMapGenerator = settingsGenerator.settingsMap;
        combineSettingsMaps(settingsMapBundle, settingsMapGenerator, settings.settingsMap, generator, nonNumericScalarModes, numericScalarModes, listModes, mapModes);

        // --- FilterList handling ---

        // PriorityWhitelists: always a set union
        Set<String> priorityWhitelistKeys = new HashSet<>(settingsBundle.priorityWhitelists.keySet());
        priorityWhitelistKeys.addAll(settingsGenerator.priorityWhitelists.keySet());
        for (String key : priorityWhitelistKeys) {
            Set<Object> listBundle = settingsBundle.priorityWhitelists.get(key);
            Set<Object> listGenerator = settingsBundle.priorityWhitelists.get(key);
            if (listBundle == null && listGenerator == null) {
                settings.priorityWhitelists.put(key, null);
            } else if (listBundle == null) {
                settings.priorityWhitelists.put(key, listGenerator);
            } else if (listGenerator == null) {
                settings.priorityWhitelists.put(key, listBundle);
            } else { // priorityWhitelist set for same key on bundle & generator settings
                settings.priorityWhitelists.put(key, Stream.concat(listBundle.stream(), listGenerator.stream()).collect(Collectors.toUnmodifiableSet()));
            }
        }

        // Whitelist/Blacklist handling:

        Set<String> allKeysBundle = settingsBundle.getAllFilterListKeys(false);
        Set<String> allKeysGenerator = settingsGenerator.getAllFilterListKeys(false);

        Set<String> allKeysInBoth = allKeysGenerator.stream().filter(allKeysBundle::contains).collect(Collectors.toUnmodifiableSet());
        allKeysBundle.removeAll(allKeysInBoth);
        allKeysGenerator.removeAll(allKeysInBoth);

        // Copy FilterList entries that are only present in one source
        settings.addFilterListEntries(allKeysBundle, settingsBundle);
        settings.addFilterListEntries(allKeysGenerator, settingsGenerator);

        // Handle entries that are present in both sources
        for (String key : allKeysInBoth) {
            FilterListCombinationMode filterListMode = filterListModes.get(key);
            if (filterListMode == null) filterListMode = generator.preSetup_defineFilterListSettingsCombinationModeDefault();

            // Full FilterList overwrites
            if (filterListMode == FilterListCombinationMode.GENERATOR_OVERWRITES_GROUP) {
                settings.whitelists.put(key, settingsGenerator.whitelists.get(key));
                settings.blacklists.put(key, settingsGenerator.blacklists.get(key));
                continue;
            } else if (filterListMode == FilterListCombinationMode.GROUP_OVERWRITES_GENERATOR) {
                settings.whitelists.put(key, settingsBundle.whitelists.get(key));
                settings.blacklists.put(key, settingsBundle.blacklists.get(key));
                continue;
            }

            // Individual configuration for whitelist/blacklist
            settings.combineIndividualWhiteBlackList(key, whitelistModes, generator.preSetup_defineWhitelistSettingsCombinationModeDefault(), settingsBundle.whitelists, settingsGenerator.whitelists, settings.whitelists);
            settings.combineIndividualWhiteBlackList(key, blacklistModes, generator.preSetup_defineBlacklistSettingsCombinationModeDefault(), settingsBundle.blacklists, settingsGenerator.blacklists, settings.blacklists);
        }

        return settings;
    }

    private void combineIndividualWhiteBlackList(String key, Map<String, WhiteBlackListCombinationMode> modes, WhiteBlackListCombinationMode defaultMode, Map<String, Set<Object>> listsBundle, Map<String, Set<Object>> listsGenerator, Map<String, Set<Object>>  destinationLists) {
        if (listsBundle.containsKey(key) && !listsGenerator.containsKey(key)) {
            destinationLists.put(key, listsBundle.get(key));
        } else if (!listsBundle.containsKey(key) && listsGenerator.containsKey(key)) {
            destinationLists.put(key, listsGenerator.get(key));
        } else if (listsBundle.containsKey(key) && listsGenerator.containsKey(key)) {
            Set<Object> listBundle = listsBundle.get(key);
            Set<Object> listGenerator = listsGenerator.get(key);
            if (listGenerator == null) {
                destinationLists.put(key, EXPLICIT_NULL_FILTERLIST_SETTINGS_IN_GENERATOR_OVERWRITE_GROUP_SETTINGS? null : listBundle);
            } else if (listBundle == null) {
                destinationLists.put(key, EXPLICIT_NULL_FILTERLIST_SETTINGS_IN_GROUP_OVERWRITE_GENERATOR_SETTINGS? null : listGenerator);
            } else {
                WhiteBlackListCombinationMode mode = modes.get(key);
                if (mode == null) mode = defaultMode;
                if (mode == WhiteBlackListCombinationMode.GENERATOR_OVERWRITES_GROUP) {
                    destinationLists.put(key, listGenerator);
                } else if (mode == WhiteBlackListCombinationMode.GROUP_OVERWRITES_GENERATOR) {
                    destinationLists.put(key, listBundle);
                } else if (mode == WhiteBlackListCombinationMode.SET_UNION) {
                    destinationLists.put(key, Stream.concat(listBundle.stream(), listGenerator.stream()).collect(Collectors.toUnmodifiableSet()));
                } else if (mode == WhiteBlackListCombinationMode.SET_INTERSECTION) {
                    destinationLists.put(key, listBundle.stream().filter(listGenerator::contains).collect(Collectors.toUnmodifiableSet()));
                } else { throw new IllegalStateException("Unknown enum value."); }
            }
        }
    }

    private void addFilterListEntries(Set<String> keys, GeneratorSettings source) {
        for (String key : keys) {
            if (source.whitelists.containsKey(key)) whitelists.put(key, source.whitelists.get(key));
            if (source.blacklists.containsKey(key)) blacklists.put(key, source.blacklists.get(key));
        }
    }

    private Set<String> getAllFilterListKeys(boolean includePriorityWhitelist) {
        Set<String> filterList = Stream.concat(whitelists.keySet().stream(), blacklists.keySet().stream()).collect(Collectors.toSet());
        if (includePriorityWhitelist) filterList.addAll(priorityWhitelists.keySet());
        return filterList;
    }

    private static void combineSettingsMaps(Map<String, Object> mapBundle, Map<String, Object> mapGenerator, Map<String, Object> mapCombined, Generator generator,
                                            Map<String, NonNumericScalarCombinationMode> nonNumericScalarModes, Map<String, NumericScalarCombinationMode> numericScalarModes,
                                            Map<String, ListCombinationMode> listModes, Map<String, MapCombinationMode> mapModes) {
        for (Map.Entry<String, Object> entry : mapBundle.entrySet()) {
            String key = entry.getKey();
            Object bundleObj = entry.getValue();
            if (mapGenerator.containsKey(key)) {
                Object generatorObj = mapGenerator.get(key);
                if (generatorObj == null) {
                    // Null setting defined in generator
                    mapCombined.put(key, EXPLICIT_NULL_SETTINGS_IN_GENERATOR_OVERWRITE_GROUP_SETTINGS? null : bundleObj);
                } else if (bundleObj == null) {
                    // Null setting defined in bundle
                    mapCombined.put(key, EXPLICIT_NULL_SETTINGS_IN_GROUP_OVERWRITE_GENERATOR_SETTINGS? null : generatorObj);
                } else if (generatorObj.getClass() == Integer.class && bundleObj.getClass() == Double.class || bundleObj.getClass() == Integer.class && generatorObj.getClass() == Double.class) {
                    // Both objects are numeric scalars of different, but compatible datatypes => Use custom setting
                    NumericScalarCombinationMode mode = numericScalarModes.get(key);
                    if (mode == null) mode = generator.preSetup_defineNumericScalarSettingsCombinationModeDefault();
                    if (mode == NumericScalarCombinationMode.GENERATOR_OVERWRITES_GROUP) {
                        mapCombined.put(key, generatorObj);
                    } else if (mode == NumericScalarCombinationMode.GROUP_OVERWRITES_GENERATOR) {
                        mapCombined.put(key, bundleObj);
                    } else {
                        double generatorNumber = ((Number) generatorObj).doubleValue();
                        double bundleNumber = ((Number) bundleObj).doubleValue();
                        double result = combineScalars(mode, generatorNumber, bundleNumber);
                        mapCombined.put(key, result);
                    }
                } else if (generatorObj.getClass() != bundleObj.getClass()) {
                    // Both objects are of incompatible datatypes => Use generator setting
                    mapCombined.put(key, generatorObj);
                } else if (bundleObj.getClass() == String.class || bundleObj.getClass() == Boolean.class) {
                    // Both objects are a non-numerical scalar (String/Boolean) => Use custom setting
                    NonNumericScalarCombinationMode mode = nonNumericScalarModes.get(key);
                    if (mode == null) mode = generator.preSetup_defineNonNumericScalarSettingsCombinationModeDefault();
                    if (mode == NonNumericScalarCombinationMode.GENERATOR_OVERWRITES_GROUP) {
                        mapCombined.put(key, generatorObj);
                    } else if (mode == NonNumericScalarCombinationMode.GROUP_OVERWRITES_GENERATOR) {
                        mapCombined.put(key, bundleObj);
                    } else { throw new IllegalStateException("Unknown enum value."); }
                } else if (bundleObj.getClass() == Integer.class || bundleObj.getClass() == Double.class) {
                    // Both objects are a numerical scalar and of same type (Integer/Double) => Use custom setting
                    NumericScalarCombinationMode mode = numericScalarModes.get(key);
                    if (mode == null) mode = generator.preSetup_defineNumericScalarSettingsCombinationModeDefault();
                    if (mode == NumericScalarCombinationMode.GENERATOR_OVERWRITES_GROUP) {
                        mapCombined.put(key, generatorObj);
                    } else if (mode == NumericScalarCombinationMode.GROUP_OVERWRITES_GENERATOR) {
                        mapCombined.put(key, bundleObj);
                    } else {
                        Number result;
                        if (bundleObj.getClass() == Integer.class) {
                            int generatorNumber = (Integer) generatorObj; int bundleNumber = (Integer) bundleObj;
                            result = combineScalars(mode, generatorNumber, bundleNumber);
                        } else {
                            double generatorNumber = (Double) generatorObj; double bundleNumber = (Double) bundleObj;
                            result = combineScalars(mode, generatorNumber, bundleNumber);
                        }
                        mapCombined.put(key, result);
                    }
                } else if (bundleObj instanceof List) {
                    // Both objects are a list => Use custom setting
                    ListCombinationMode mode = listModes.get(key);
                    if (mode == null) mode = generator.preSetup_defineListSettingsCombinationModeDefault();
                    if (mode == ListCombinationMode.GENERATOR_OVERWRITES_GROUP) {
                        mapCombined.put(key, generatorObj);
                    } else if (mode == ListCombinationMode.GROUP_OVERWRITES_GENERATOR) {
                        mapCombined.put(key, bundleObj);
                    } else if (mode == ListCombinationMode.GENERATOR_APPEND_GROUP) {
                        ArrayList<?> generatorList = (ArrayList<?>) generatorObj;
                        ArrayList<?> bundleList = (ArrayList<?>) bundleObj;
                        mapCombined.put(key, Stream.concat(generatorList.stream(), bundleList.stream()).collect(Collectors.toCollection(ArrayList::new)));
                    } else if (mode == ListCombinationMode.GROUP_APPEND_GENERATOR) {
                        ArrayList<?> generatorList = (ArrayList<?>) generatorObj;
                        ArrayList<?> bundleList = (ArrayList<?>) bundleObj;
                        mapCombined.put(key, Stream.concat(bundleList.stream(), generatorList.stream()).collect(Collectors.toCollection(ArrayList::new)));
                    } else if (mode == ListCombinationMode.SET_UNION) {
                        Set<Object> generatorSet = new HashSet<>((ArrayList<?>) generatorObj);
                        ArrayList<?> bundleList = (ArrayList<?>) bundleObj;
                        generatorSet.addAll(bundleList);
                        mapCombined.put(key, generatorSet);
                    } else if (mode == ListCombinationMode.SET_INTERSECTION) {
                        Set<Object> generatorSet = new HashSet<>((ArrayList<?>) generatorObj);
                        ArrayList<?> bundleList = (ArrayList<?>) bundleObj;
                        generatorSet.retainAll(bundleList);
                        mapCombined.put(key, generatorSet);
                    } else { throw new IllegalStateException("Unknown enum value."); }
                } else if (bundleObj instanceof Map) {
                    // Both objects are a map => Use custom setting
                    MapCombinationMode mode = mapModes.get(key);
                    if (mode == null) mode = generator.preSetup_defineMapSettingsCombinationModeDefault();
                    if (mode == MapCombinationMode.GENERATOR_OVERWRITES_GROUP) {
                        mapCombined.put(key, generatorObj);
                    } else if (mode == MapCombinationMode.GROUP_OVERWRITES_GENERATOR) {
                        mapCombined.put(key, bundleObj);
                    } else if (mode == MapCombinationMode.SAME_AS_MAIN_SETTINGS) {
                        // Recursively combine the sub-maps in the same way as the main settings map
                        Map<String, Object> bundleMap = (Map<String, Object>) bundleObj;
                        Map<String, Object> generatorMap = (Map<String, Object>) generatorObj;
                        HashMap<String, Object> combinedMap = new HashMap<>();
                        combineSettingsMaps(bundleMap, generatorMap, combinedMap, generator, nonNumericScalarModes, numericScalarModes, listModes, mapModes);
                        mapCombined.put(key, combinedMap);
                    } else { throw new IllegalStateException("Unknown enum value."); }
                } else {
                    // Both objects are of the same, but unknown class => Use generator setting
                    mapCombined.put(key, generatorObj);
                }
            } else {
                // Setting only defined in bundle => Save it without any transformation
                mapCombined.put(key, bundleObj);
            }
        }

        // All Settings only defined in generator
        for (Map.Entry<String, Object> entry : mapGenerator.entrySet()) {
            if (!mapBundle.containsKey(entry.getKey())) {
                // Setting only defined in generator => save it without any transformation
                mapCombined.put(entry.getKey(), entry.getValue());
            }
        }
    }

    private static double combineScalars(NumericScalarCombinationMode mode, double generatorNumber, double bundleNumber) {
        double result;
        if (mode == NumericScalarCombinationMode.ADD) { result = generatorNumber + bundleNumber;
        } else if (mode == NumericScalarCombinationMode.MULTIPLY) { result = generatorNumber * bundleNumber;
        } else if (mode == NumericScalarCombinationMode.GENERATOR_MINUS_GROUP) { result = generatorNumber - bundleNumber;
        } else if (mode == NumericScalarCombinationMode.GROUP_MINUS_GENERATOR) { result = bundleNumber - generatorNumber;
        } else if (mode == NumericScalarCombinationMode.GENERATOR_DIVIDE_BY_GROUP) { result = generatorNumber / bundleNumber;
        } else if (mode == NumericScalarCombinationMode.GROUP_DIVIDE_BY_GENERATOR) { result = bundleNumber / generatorNumber;
        } else { throw new IllegalStateException("Unknown enum value."); }
        return result;
    }
    private static Number combineScalars(NumericScalarCombinationMode mode, int generatorNumber, int bundleNumber) {
        Number result;
        if (mode == NumericScalarCombinationMode.ADD) { result = generatorNumber + bundleNumber;
        } else if (mode == NumericScalarCombinationMode.MULTIPLY) { result = generatorNumber * bundleNumber;
        } else if (mode == NumericScalarCombinationMode.GENERATOR_MINUS_GROUP) { result = generatorNumber - bundleNumber;
        } else if (mode == NumericScalarCombinationMode.GROUP_MINUS_GENERATOR) { result = bundleNumber - generatorNumber;
        } else if (mode == NumericScalarCombinationMode.GENERATOR_DIVIDE_BY_GROUP) { result = ((double) generatorNumber / (double) bundleNumber);
        } else if (mode == NumericScalarCombinationMode.GROUP_DIVIDE_BY_GENERATOR) { result = ((double) bundleNumber / (double) generatorNumber);
        } else { throw new IllegalStateException("Unknown enum value."); }
        return result;
    }

    // Combination modes
    public enum NonNumericScalarCombinationMode { GENERATOR_OVERWRITES_GROUP, GROUP_OVERWRITES_GENERATOR }
    public enum NumericScalarCombinationMode { GENERATOR_OVERWRITES_GROUP, GROUP_OVERWRITES_GENERATOR, ADD, MULTIPLY, GENERATOR_MINUS_GROUP, GROUP_MINUS_GENERATOR, GENERATOR_DIVIDE_BY_GROUP, GROUP_DIVIDE_BY_GENERATOR }
    public enum ListCombinationMode { GENERATOR_OVERWRITES_GROUP, GROUP_OVERWRITES_GENERATOR, GENERATOR_APPEND_GROUP, GROUP_APPEND_GENERATOR, SET_UNION, SET_INTERSECTION }
    public enum MapCombinationMode { GENERATOR_OVERWRITES_GROUP, GROUP_OVERWRITES_GENERATOR, SAME_AS_MAIN_SETTINGS}

    // FilterList combination modes
    public enum FilterListCombinationMode { GENERATOR_OVERWRITES_GROUP, GROUP_OVERWRITES_GENERATOR, CONFIGURE_INDIVIDUALLY }
    // the following setting WhiteBlackListCombinationMode and is only relevant if this is set to CONFIGURE_INDIVIDUALLY for any FilterList settings key

    public enum WhiteBlackListCombinationMode { GENERATOR_OVERWRITES_GROUP, GROUP_OVERWRITES_GENERATOR, SET_UNION, SET_INTERSECTION }
}
