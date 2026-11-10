/*
 * Copyright (c) 2026 Nicholas DellaGrotte
 * SPDX-License-Identifier: GPL-3.0-only
 */

package io.github.ndellagrotte.ordinaryai.ai.react;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DodgeMathTest {

    private static final double HALF_WIDTH = 0.8;
    private static final double RANGE = 24.0;

    /** An arrow at the origin flying along positive x. */
    private static boolean inPath(double dx, double dz) {
        return DodgeMath.isInPath(dx, dz, 1.0, 0.0, HALF_WIDTH, RANGE);
    }

    @Test
    void pointsStraightAheadAreInThePath() {
        assertThat(inPath(1.0, 0.0)).isTrue();
        assertThat(inPath(10.0, 0.0)).isTrue();
        assertThat(inPath(23.9, 0.0)).isTrue();
    }

    @Test
    void widthIsMeasuredFromTheCentreLine() {
        assertThat(inPath(10.0, 0.7)).isTrue();
        assertThat(inPath(10.0, -0.7)).isTrue();
        assertThat(inPath(10.0, 0.9)).isFalse();
        assertThat(inPath(10.0, -0.9)).isFalse();
    }

    @Test
    void pointsBehindTheArrowAreNotInThePath() {
        assertThat(inPath(-1.0, 0.0)).isFalse();
        assertThat(inPath(-10.0, 0.2)).isFalse();
        assertThat(inPath(0.0, 0.5)).isFalse();
    }

    @Test
    void pointsBeyondTheRangeAreIgnored() {
        assertThat(inPath(24.0, 0.0)).isFalse();
        assertThat(inPath(100.0, 0.0)).isFalse();
    }

    @Test
    void theOriginItselfIsNotInThePath() {
        assertThat(inPath(0.0, 0.0)).isFalse();
    }

    @Test
    void worksForDiagonalDirections() {
        double unit = Math.sqrt(0.5);
        assertThat(DodgeMath.isInPath(5.0, 5.0, unit, unit, HALF_WIDTH, RANGE)).isTrue();
        assertThat(DodgeMath.isInPath(5.0, -5.0, unit, unit, HALF_WIDTH, RANGE)).isFalse();
        assertThat(DodgeMath.isInPath(-5.0, -5.0, unit, unit, HALF_WIDTH, RANGE)).isFalse();
    }

    @Test
    void horizontalSpeedIgnoresNothingButHeight() {
        assertThat(DodgeMath.horizontalSpeed(3.0, 4.0)).isEqualTo(5.0);
        assertThat(DodgeMath.horizontalSpeed(0.0, 0.0)).isZero();
    }

}
