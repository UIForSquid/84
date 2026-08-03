package mindmap84;

/** Shared text-parsing helpers used by the checklist + datalist panels. */
final class Parse {
    /** A header line starts or ends with = _ | ~ ; returns the cleaned title, "" for a bare divider, or null. */
    static String headerName(String line) {
        String t = line.trim();
        if (t.isEmpty()) return null;
        if (!t.matches(".*[=_|~].*")) return null;
        boolean startsMark = t.matches("^[=_|~].*");
        boolean endsMark = t.matches(".*[=_|~]$");
        if (!startsMark && !endsMark) return null;
        return t.replaceAll("^[=_|~\\s]+", "").replaceAll("[=_|~\\s]+$", "").trim();
    }

    static String clean(String s) {
        return s.trim().replaceAll("^(?i)and\\s+", "").trim();
    }

    private Parse() {}
}
