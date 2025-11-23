package org.texttechnologylab.udav.generators;

import lombok.Getter;
import org.texttechnologylab.udav.generators.common_properties.CommonProperties;
import org.texttechnologylab.udav.generators.settings.GeneratorSettings;
import org.texttechnologylab.udav.generators.sources.Source;
import org.texttechnologylab.udav.pipeline.JSONView;
import org.texttechnologylab.udav.sources.DBAccess;

import java.sql.SQLException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.texttechnologylab.udav.generators.settings.GeneratorSettings.NonNumericScalarCombinationMode;
import org.texttechnologylab.udav.generators.settings.GeneratorSettings.NumericScalarCombinationMode;
import org.texttechnologylab.udav.generators.settings.GeneratorSettings.ListCombinationMode;
import org.texttechnologylab.udav.generators.settings.GeneratorSettings.MapCombinationMode;
import org.texttechnologylab.udav.generators.settings.GeneratorSettings.FilterListCombinationMode;
import org.texttechnologylab.udav.generators.settings.GeneratorSettings.WhiteBlackListCombinationMode;

@Getter
public abstract class Generator {
    public static final String GENERATORS_PACKAGE_PATH = "org.texttechnologylab.udav.generators";

    protected final String id;
    protected final JSONView configGenerator;
    protected final JSONView configBundle;
    protected final DBAccess dbAccess;
    protected final GeneratorSettings settingsGenerator;
    protected final GeneratorSettings settingsBundle;
    protected final GeneratorSettings settings;

    protected Source source;


    public Generator(String id, JSONView configGenerator, JSONView configBundle, GeneratorSettings settingsBundle, DBAccess dbAccess) {
        this.id = id;
        this.configGenerator = configGenerator;
        this.configBundle = configBundle;
        this.settingsBundle = settingsBundle;
        this.dbAccess = dbAccess;

        this.settingsGenerator = GeneratorSettings.fromConfig(configGenerator);
        this.settings = GeneratorSettings.fromGenerator(this);
    }

    // --- ADVANCED Pre-Setup methods: ONLY OVERWRITE THEM IF YOU KNOW WHAT YOU ARE DOING! ---

    // CommonProperties related methods
    public Set<Class<? extends CommonProperties>> preSetup_getAllCommonPropertyClasses() { return Set.of(); }
    public void preSetup_setCommonPropertiesObj(CommonProperties commonProperties) {}

    // GeneratorSettings related methods
    // TODO: Whitelist/Blacklist modes
    public Map<String, NonNumericScalarCombinationMode> preSetup_defineAllNonNumericScalarSettingsCombinationMode() {
        return preSetup_defineAdditionalNonNumericScalarSettingsCombinationMode(); // TODO: Define some global defaults
    }
    public Map<String, NumericScalarCombinationMode> preSetup_defineAllNumericScalarSettingsCombinationMode() {
        return preSetup_defineAdditionalNumericScalarSettingsCombinationMode(); // TODO: Define some global defaults
    }
    public Map<String, ListCombinationMode> preSetup_defineAllListSettingsCombinationMode() {
        return preSetup_defineAdditionalListSettingsCombinationMode(); // TODO: Define some global defaults
    }
    public Map<String, MapCombinationMode> preSetup_defineAllMapSettingsCombinationMode() {
        return preSetup_defineAdditionalMapSettingsCombinationMode(); // TODO: Define some global defaults
    }
    public Map<String, FilterListCombinationMode> preSetup_defineAllFilterListSettingsCombinationMode() {
        return preSetup_defineAdditionalFilterListSettingsCombinationMode(); // TODO: Define some global defaults
    }
    public Map<String, WhiteBlackListCombinationMode> preSetup_defineAllWhitelistSettingsCombinationMode() {
        return preSetup_defineAdditionalWhitelistSettingsCombinationMode(); // TODO: Define some global defaults
    }
    public Map<String, WhiteBlackListCombinationMode> preSetup_defineAllBlacklistSettingsCombinationMode() {
        return preSetup_defineAdditionalBlacklistSettingsCombinationMode(); // TODO: Define some global defaults
    }
    public NonNumericScalarCombinationMode preSetup_defineNonNumericScalarSettingsCombinationModeDefault() { return NonNumericScalarCombinationMode.GENERATOR_OVERWRITES_GROUP; }
    public NumericScalarCombinationMode preSetup_defineNumericScalarSettingsCombinationModeDefault() { return NumericScalarCombinationMode.GENERATOR_OVERWRITES_GROUP; }
    public ListCombinationMode preSetup_defineListSettingsCombinationModeDefault() { return ListCombinationMode.GENERATOR_OVERWRITES_GROUP; }
    public MapCombinationMode preSetup_defineMapSettingsCombinationModeDefault() { return MapCombinationMode.SAME_AS_MAIN_SETTINGS; }
    public FilterListCombinationMode preSetup_defineFilterListSettingsCombinationModeDefault() { return FilterListCombinationMode.CONFIGURE_INDIVIDUALLY; }
    public WhiteBlackListCombinationMode preSetup_defineWhitelistSettingsCombinationModeDefault() { return WhiteBlackListCombinationMode.SET_INTERSECTION; }
    public WhiteBlackListCombinationMode preSetup_defineBlacklistSettingsCombinationModeDefault() { return WhiteBlackListCombinationMode.SET_UNION; }



    // --- Pre-Setup methods ---

    public Set<Class<? extends Source>> preSetup_getAllSourceClasses() { return Set.of(); }
    public Set<String> preSetup_defineAllFilterListKeys() { return Set.of("files", "categories"); }
    public Map<String, NonNumericScalarCombinationMode> preSetup_defineAdditionalNonNumericScalarSettingsCombinationMode() { return Map.of(); }
    public Map<String, NumericScalarCombinationMode> preSetup_defineAdditionalNumericScalarSettingsCombinationMode() { return Map.of(); }
    public Map<String, ListCombinationMode> preSetup_defineAdditionalListSettingsCombinationMode() { return Map.of(); }
    public Map<String, MapCombinationMode> preSetup_defineAdditionalMapSettingsCombinationMode() { return Map.of(); }
    public Map<String, FilterListCombinationMode> preSetup_defineAdditionalFilterListSettingsCombinationMode() { return Map.of(); }
    public Map<String, WhiteBlackListCombinationMode> preSetup_defineAdditionalWhitelistSettingsCombinationMode() { return Map.of(); }
    public Map<String, WhiteBlackListCombinationMode> preSetup_defineAdditionalBlacklistSettingsCombinationMode() { return Map.of(); }


    // --- Setup methods ---

    public abstract void setup() throws SQLException;
    public void setup_step1() throws SQLException { setup(); }
    public void setup_step2() throws SQLException {}
    public void setup_step3() throws SQLException {}


    // --- Post-Setup methods ---

    public abstract void writeToDB() throws SQLException;






    // --- Methods that should never be overwritten in generator implementations: ---

    public final void setSource(Source source) {
        if (this.source != null) {
            throw new IllegalArgumentException("setSource called on generator with source already set before.");
        }
        Set<Class<? extends Source>> allowedSourceClasses = preSetup_getAllSourceClasses();
        if (source == null || !allowedSourceClasses.contains(source.getClass())) {
            throw new IllegalArgumentException("Invalid source for this generator type.");
        }
        this.source = source;
    }

    protected final Set<String> generateFilterListSuffixCombinations(Set<String> suffixes) {
        Set<String> allListKeys = preSetup_defineAllFilterListKeys();
        return generateListSuffixCombinations(suffixes, allListKeys);
    }

    protected static Set<String> generateListSuffixCombinations(Set<String> suffixes, Set<String> allListKeys) {
        Set<String> outputSet = new HashSet<>();
        for (String k : allListKeys) {
            for (String suffix : suffixes) outputSet.add(k + suffix);
        }
        return outputSet;
    }
}
