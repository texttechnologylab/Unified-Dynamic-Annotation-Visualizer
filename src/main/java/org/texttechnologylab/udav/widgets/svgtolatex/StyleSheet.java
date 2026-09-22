package org.texttechnologylab.udav.widgets.svgtolatex;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The CSS declared by {@code <style>} elements inside the document.
 * <p>
 * Generated SVG (Mermaid, draw.io) styles most of its elements through a
 * {@code class} and a stylesheet rather than through {@code fill} attributes.
 * {@link #applyTo} merges the matched declarations into each element's own
 * {@code style} attribute, with the inline declarations kept first.
 * {@link ParseUtils#getStyleOrAttr} returns the first match, which yields the CSS
 * cascade (inline style, then stylesheet rule, then presentation attribute) without
 * further changes elsewhere; inheritance keeps working because inherited properties
 * are read through the same accessor.
 * <p>
 * Supported: type ({@code rect}), class ({@code .node}), id ({@code #a}) and
 * universal ({@code *}) selectors, compounds of those ({@code rect.big}), descendant
 * ({@code g .node}) and child ({@code g > rect}) combinators, selector lists,
 * {@code !important}. Specificity and source order decide the winner.
 * Not supported: at-rules including {@code @media}, attribute and pseudo-class
 * selectors, sibling combinators. Rules using them are skipped and the element
 * keeps what its presentation attributes say.
 */
public final class StyleSheet {

    /** One parsed rule: a single selector plus the declarations it carries. */
    private record Rule(Selector selector, Map<String, String> declarations, int order) {}

    /** One step of a selector, e.g. the {@code .node} in {@code g > .node}. */
    private record Step(String tag, String id, List<String> classes, boolean childOfPrevious) {

        boolean matches(Element el) {
            if (tag != null && !tag.equals(localName(el))) return false;
            if (id != null && !id.equals(el.getAttribute("id"))) return false;
            if (!classes.isEmpty()) {
                List<String> actual = Arrays.asList(el.getAttribute("class").trim().split("\\s+"));
                for (String required : classes) {
                    if (!actual.contains(required)) return false;
                }
            }
            return true;
        }
    }

    /** A full selector: steps left to right, plus its CSS specificity. */
    private record Selector(List<Step> steps, int specificity) {}

    private final List<Rule> rules;

    private StyleSheet(List<Rule> rules) {
        this.rules = rules;
    }

    /** True when the document declared no usable CSS, so {@link #applyTo} is a no-op. */
    public boolean isEmpty() {
        return rules.isEmpty();
    }

    // -----------------------------------------------------------------------
    // Collection
    // -----------------------------------------------------------------------

    /** Parses every {@code <style>} element in the document, in document order. */
    public static StyleSheet collect(Element root) {
        StringBuilder css = new StringBuilder();
        collectStyleText(root, css);
        List<Rule> rules = css.length() == 0 ? List.of() : parse(css.toString());
        return new StyleSheet(rules);
    }

    private static void collectStyleText(Node node, StringBuilder sink) {
        if (node instanceof Element el && "style".equals(localName(el))) {
            // getTextContent unwraps CDATA, which is how most exporters wrap their CSS.
            sink.append(el.getTextContent()).append('\n');
            return;
        }
        NodeList kids = node.getChildNodes();
        for (int i = 0; i < kids.getLength(); i++) collectStyleText(kids.item(i), sink);
    }

    // -----------------------------------------------------------------------
    // Application
    // -----------------------------------------------------------------------

    /**
     * Merges the matched declarations into every element's {@code style} attribute.
     * Call once, before any pass that reads styling.
     */
    public void applyTo(Element root) {
        if (rules.isEmpty()) return;
        apply(root, new ArrayList<>());
    }

    private void apply(Element el, List<Element> ancestors) {
        Map<String, String> matched = declarationsFor(el, ancestors);
        if (!matched.isEmpty()) {
            String inline = el.getAttribute("style").trim();
            StringBuilder merged = new StringBuilder();
            // Inline first: getStyleOrAttr takes the first match, so this is what makes
            // an inline declaration beat the stylesheet.
            if (!inline.isEmpty()) {
                merged.append(inline);
                if (!inline.endsWith(";")) merged.append(';');
            }
            for (Map.Entry<String, String> d : matched.entrySet()) {
                merged.append(d.getKey()).append(':').append(d.getValue()).append(';');
            }
            el.setAttribute("style", merged.toString());
        }

        ancestors.add(el);
        NodeList kids = el.getChildNodes();
        for (int i = 0; i < kids.getLength(); i++) {
            if (kids.item(i) instanceof Element child) apply(child, ancestors);
        }
        ancestors.remove(ancestors.size() - 1);
    }

    /**
     * The declarations that win for one element, in cascade order.
     *
     * @param ancestors the element's ancestor chain, outermost first
     */
    private Map<String, String> declarationsFor(Element el, List<Element> ancestors) {
        List<Rule> hits = new ArrayList<>();
        for (Rule rule : rules) {
            if (matches(rule.selector(), el, ancestors)) hits.add(rule);
        }
        if (hits.isEmpty()) return Map.of();

        // Weakest first, so later puts() overwrite earlier ones.
        hits.sort(Comparator.comparingInt((Rule r) -> r.selector().specificity())
                .thenComparingInt(Rule::order));

        Map<String, String> winning = new LinkedHashMap<>();
        for (Rule rule : hits) winning.putAll(rule.declarations());
        return winning;
    }

    // -----------------------------------------------------------------------
    // Selector matching
    // -----------------------------------------------------------------------

    /**
     * Matches right to left: the last step must match the element itself, then each
     * earlier step must match somewhere up the ancestor chain, the next one up for a
     * child combinator and any of them for a descendant combinator.
     */
    private static boolean matches(Selector selector, Element el, List<Element> ancestors) {
        List<Step> steps = selector.steps();
        if (steps.isEmpty() || !steps.get(steps.size() - 1).matches(el)) return false;
        return matchesUpwards(steps, steps.size() - 1, ancestors, ancestors.size());
    }

    /**
     * @param stepIndex   the step already matched
     * @param ancestorEnd exclusive upper bound of the ancestor range still available
     */
    private static boolean matchesUpwards(List<Step> steps, int stepIndex,
                                          List<Element> ancestors, int ancestorEnd) {
        if (stepIndex == 0) return true; // every step consumed

        Step matchedStep = steps.get(stepIndex);
        Step next = steps.get(stepIndex - 1);

        if (matchedStep.childOfPrevious()) {
            // "A > B": the parent, and only the parent, must match A.
            int parent = ancestorEnd - 1;
            if (parent < 0 || !next.matches(ancestors.get(parent))) return false;
            return matchesUpwards(steps, stepIndex - 1, ancestors, parent);
        }
        // "A B": any ancestor may match A. Try the nearest first, and backtrack, since
        // a nearer match can strand an earlier step that a farther one would satisfy.
        for (int i = ancestorEnd - 1; i >= 0; i--) {
            if (next.matches(ancestors.get(i)) && matchesUpwards(steps, stepIndex - 1, ancestors, i)) {
                return true;
            }
        }
        return false;
    }

    // -----------------------------------------------------------------------
    // Parsing
    // -----------------------------------------------------------------------

    private static final Pattern COMMENT = Pattern.compile("(?s)/\\*.*?\\*/");
    /** A selector step: optional tag, then any number of #id / .class parts. */
    private static final Pattern STEP = Pattern.compile(
            "^(\\*|[A-Za-z][\\w-]*)?((?:[.#][\\w-]+)*)$");
    /** Anything in a selector this parser does not interpret. */
    private static final Pattern UNSUPPORTED_SELECTOR = Pattern.compile("[\\[:+~]");

    private static List<Rule> parse(String css) {
        css = COMMENT.matcher(css).replaceAll("");
        List<Rule> rules = new ArrayList<>();
        int i = 0, order = 0;

        while (i < css.length()) {
            char c = css.charAt(i);
            if (Character.isWhitespace(c)) { i++; continue; }

            // At-rules (@font-face, @media, @import) are skipped whole: a media query
            // needs a device to evaluate, and the rest declare resources this converter
            // cannot use.
            if (c == '@') {
                int semicolon = css.indexOf(';', i);
                int brace = css.indexOf('{', i);
                if (brace < 0 || (semicolon >= 0 && semicolon < brace)) {
                    i = semicolon < 0 ? css.length() : semicolon + 1;
                } else {
                    i = skipBlock(css, brace);
                }
                continue;
            }

            int open = css.indexOf('{', i);
            if (open < 0) break;
            int close = matchingBrace(css, open);
            if (close < 0) break;

            String selectorList = css.substring(i, open).trim();
            String body = css.substring(open + 1, close);
            Map<String, String> declarations = parseDeclarations(body);

            if (!declarations.isEmpty()) {
                for (String raw : selectorList.split(",")) {
                    Selector selector = parseSelector(raw.trim());
                    if (selector != null) rules.add(new Rule(selector, declarations, order++));
                }
            }
            i = close + 1;
        }
        return rules;
    }

    /** Returns the index just past the block whose opening brace is at {@code open}. */
    private static int skipBlock(String css, int open) {
        int close = matchingBrace(css, open);
        return close < 0 ? css.length() : close + 1;
    }

    private static int matchingBrace(String css, int open) {
        int depth = 0;
        for (int i = open; i < css.length(); i++) {
            char c = css.charAt(i);
            if (c == '{') depth++;
            else if (c == '}' && --depth == 0) return i;
        }
        return -1;
    }

    private static Map<String, String> parseDeclarations(String body) {
        Map<String, String> declarations = new LinkedHashMap<>();
        for (String part : body.split(";")) {
            int colon = part.indexOf(':');
            if (colon <= 0) continue;
            String property = part.substring(0, colon).trim().toLowerCase(Locale.ROOT);
            String value = part.substring(colon + 1).trim();
            if (property.isEmpty() || value.isEmpty()) continue;
            // !important only matters against other CSS, and the merge order already
            // settles that; strip it so downstream value parsing is not confused by it.
            value = value.replaceAll("(?i)\\s*!\\s*important\\s*$", "").trim();
            if (!value.isEmpty()) declarations.put(property, value);
        }
        return declarations;
    }

    /** Returns null for a selector this parser does not interpret. */
    private static Selector parseSelector(String raw) {
        if (raw.isEmpty() || UNSUPPORTED_SELECTOR.matcher(raw).find()) return null;

        // Give combinators surrounding space so a single split handles both forms.
        String[] tokens = raw.replace(">", " > ").trim().split("\\s+");
        List<Step> steps = new ArrayList<>();
        int ids = 0, classes = 0, types = 0;
        boolean childCombinator = false;

        for (String token : tokens) {
            if (token.equals(">")) { childCombinator = true; continue; }

            Matcher m = STEP.matcher(token);
            if (!m.matches()) return null;

            String tag = m.group(1);
            if ("*".equals(tag)) tag = null;
            else if (tag != null) { tag = tag.toLowerCase(Locale.ROOT); types++; }

            String id = null;
            List<String> classNames = new ArrayList<>();
            Matcher parts = Pattern.compile("([.#])([\\w-]+)").matcher(m.group(2));
            while (parts.find()) {
                if (".".equals(parts.group(1))) { classNames.add(parts.group(2)); classes++; }
                else { id = parts.group(2); ids++; }
            }

            steps.add(new Step(tag, id, classNames, childCombinator));
            childCombinator = false;
        }
        if (steps.isEmpty()) return null;

        // CSS specificity, packed so a plain integer comparison orders it correctly.
        int specificity = ids * 10_000 + classes * 100 + types;
        return new Selector(List.copyOf(steps), specificity);
    }

    /** Tag name with any namespace prefix removed, lower-cased. */
    private static String localName(Element el) {
        return el.getTagName().replaceFirst(".*:", "").toLowerCase(Locale.ROOT);
    }
}
