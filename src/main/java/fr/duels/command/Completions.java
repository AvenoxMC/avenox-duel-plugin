package fr.duels.command;

import java.util.ArrayList;
import java.util.List;

final class Completions {

    private Completions() {
    }

    static List<String> filter(List<String> options, String prefix) {
        List<String> out = new ArrayList<>();
        String lower = prefix.toLowerCase();
        for (String option : options) {
            if (option.toLowerCase().startsWith(lower)) {
                out.add(option);
            }
        }
        return out;
    }
}
