package org.texttechnologylab.udav.widgets.tools;

public class Svg2TikzFixes {

    public static String preRun_hyphenMinusFix(String svg) {
        return svg.replace("\u2212", "-");
    }

    public static String postRun_southAnchorFix(String tikz) {
        StringBuilder result = new StringBuilder();
        int idx = 0;
        while (idx < tikz.length()) {
            int firstEndScope = tikz.indexOf("\\end{scope}", idx);
            if (firstEndScope == -1) {
                result.append(tikz.substring(idx).replace("anchor=south", "anchor=north"));
                break;
            }
            // Replace in the part before first \end{scope}
            result.append(tikz.substring(idx, firstEndScope).replace("anchor=south", "anchor=north"));
            result.append("\\end{scope}");
            int afterFirst = firstEndScope + "\\end{scope}".length();
            // Scan for only whitespace until next non-whitespace character
            int scan = afterFirst;
            while (scan < tikz.length() && Character.isWhitespace(tikz.charAt(scan))) scan++;
            // If next is another \end{scope} and no other '\' between, stop replacing
            if (tikz.startsWith("\\end{scope}", scan)) {
                // Check if there is any other '\' between afterFirst and scan
                boolean otherBackslash = false;
                for (int i = afterFirst; i < scan; i++) {
                    if (tikz.charAt(i) == '\\') {
                        otherBackslash = true;
                        break;
                    }
                }
                if (!otherBackslash) {
                    result.append(tikz.substring(afterFirst, scan)); // append whitespace
                    result.append("\\end{scope}");
                    scan += "\\end{scope}".length();
                    result.append(tikz.substring(scan));
                    break;
                }
            }
            idx = afterFirst;
        }
        return result.toString();
    }
}
