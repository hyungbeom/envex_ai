package com.progist.envex_ai.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class CompanyBulletParser {

    private static final Pattern BOLD_NAME = Pattern.compile("\\*\\*(.+?)\\*\\*");

    record CompanyBullet(String companyName, String description) {
    }

    record ParsedAi(String intro, List<CompanyBullet> bullets, String outro) {
    }

    private CompanyBulletParser() {
    }

    static ParsedAi parse(String text) {
        if (text == null || text.isBlank()) {
            return new ParsedAi(null, List.of(), null);
        }

        String[] lines = text.split("\n", -1);
        StringBuilder intro = new StringBuilder();
        List<CompanyBullet> bullets = new ArrayList<>();
        StringBuilder outro = new StringBuilder();

        int index = 0;
        while (index < lines.length && !isBulletLine(lines[index])) {
            if (intro.length() > 0) {
                intro.append('\n');
            }
            intro.append(lines[index]);
            index++;
        }

        while (index < lines.length) {
            if (!isBulletLine(lines[index])) {
                break;
            }
            String firstLine = lines[index].trim().substring(2).trim();
            String companyName = extractCompanyName(firstLine);
            String description = extractInlineDescription(firstLine);

            index++;
            StringBuilder descriptionBlock = new StringBuilder();
            if (description != null && !description.isBlank()) {
                descriptionBlock.append(description);
            }
            while (index < lines.length && !isBulletLine(lines[index])) {
                String line = lines[index].trim();
                if (!line.isBlank() && !isMetadataLine(line)) {
                    if (descriptionBlock.length() > 0) {
                        descriptionBlock.append('\n');
                    }
                    descriptionBlock.append(line);
                }
                index++;
            }

            if (companyName != null && !companyName.isBlank()) {
                bullets.add(new CompanyBullet(
                        companyName.trim(),
                        cleanDescription(descriptionBlock.toString())
                ));
            }
        }

        while (index < lines.length) {
            if (outro.length() > 0) {
                outro.append('\n');
            }
            outro.append(lines[index]);
            index++;
        }

        return new ParsedAi(
                blankToNull(intro.toString()),
                bullets,
                blankToNull(outro.toString())
        );
    }

    private static boolean isBulletLine(String line) {
        return line != null && line.trim().startsWith("- ");
    }

    private static String extractCompanyName(String bulletContent) {
        Matcher bold = BOLD_NAME.matcher(bulletContent);
        if (bold.find()) {
            return bold.group(1).trim();
        }
        Matcher split = Pattern.compile("^(.+?)\\s*(?:—|--|-)\\s*.+$").matcher(bulletContent);
        if (split.matches()) {
            return split.group(1).trim();
        }
        return bulletContent.trim();
    }

    private static String extractInlineDescription(String bulletContent) {
        for (String separator : new String[]{" — ", " -- ", " - "}) {
            int index = bulletContent.indexOf(separator);
            if (index >= 0) {
                return bulletContent.substring(index + separator.length()).trim();
            }
        }
        int dash = bulletContent.indexOf('—');
        if (dash >= 0) {
            return bulletContent.substring(dash + 1).trim();
        }
        return null;
    }

    private static boolean isMetadataLine(String line) {
        String lower = line.toLowerCase(Locale.ROOT);
        return lower.contains("부스 번호")
                || lower.contains("부스번호")
                || lower.contains("연락처")
                || lower.startsWith("회사 소개")
                || lower.contains("map?booth=");
    }

    private static String cleanDescription(String description) {
        if (description == null || description.isBlank()) {
            return "";
        }
        String[] lines = description.split("\n");
        StringBuilder cleaned = new StringBuilder();
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isBlank() || isMetadataLine(trimmed)) {
                continue;
            }
            if (cleaned.length() > 0) {
                cleaned.append('\n');
            }
            cleaned.append(trimmed);
        }
        return cleaned.toString().trim();
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
