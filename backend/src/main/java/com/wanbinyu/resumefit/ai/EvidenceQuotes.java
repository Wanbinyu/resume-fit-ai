package com.wanbinyu.resumefit.ai;

import java.util.regex.Pattern;

/** Bounded verbatim excerpt around a keyword; long imported lines cannot multiply report size. */
public final class EvidenceQuotes {
    private EvidenceQuotes() {}
    public static String forKeyword(String text,String keyword) {
        var pattern=Pattern.compile("(?<![A-Za-z0-9])"+Pattern.quote(keyword)+"(?![A-Za-z0-9])",Pattern.CASE_INSENSITIVE);
        for(String line:text.lines().toList()) {
            var matcher=pattern.matcher(line);
            if(!matcher.find()) continue;
            // Regex offsets refer to the original text, including Unicode whose lowercase form changes length.
            int start=Math.max(0,matcher.start()-60),end=Math.min(line.length(),start+240);
            return line.substring(start,end);
        }
        return "";
    }
}
