package com.mahghuuuls.somepackdefaults.report;

import com.mahghuuuls.somepackdefaults.rules.Problem;

import java.util.List;

/**
 * The one place that stops a start because of configuration problems. Callers pass the problems
 * and do not know how the stop happens on each side.
 */
public interface StartBlocker {

    /**
     * Stops a dedicated server before any world loads. The problems are already in the log; this
     * call does not return in the real game.
     */
    void stopBeforeWorldLoad(List<Problem> problems);
}
