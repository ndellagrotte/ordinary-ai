/*
 * Copyright (c) 2026 Nicholas DellaGrotte
 * SPDX-License-Identifier: GPL-3.0-only
 */

package io.github.ndellagrotte.ordinaryai.config;

import io.github.ndellagrotte.ordinaryai.config.BlockTargetSyntax.Condition;
import io.github.ndellagrotte.ordinaryai.config.BlockTargetSyntax.Target;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BlockTargetSyntaxTest {

    private final List<String> warnings = new ArrayList<>();

    @Test
    void splitsSimpleLists() {
        assertThat(BlockTargetSyntax.splitLine("minecraft:torch, minecraft:bed ,minecraft:cake",
                warnings::add)).containsExactly("minecraft:torch", "minecraft:bed", "minecraft:cake");
        assertThat(warnings).isEmpty();
    }

    @Test
    void keepsCommasInsideBracketsTogether() {
        assertThat(BlockTargetSyntax.splitLine("a:door[half=upper, open=true], a:lever[powered=false],a:tnt",
                warnings::add)).containsExactly("a:door[half=upper,open=true]", "a:lever[powered=false]", "a:tnt");
        assertThat(warnings).isEmpty();
    }

    @Test
    void dropsAnUnterminatedStateAndSaysSo() {
        assertThat(BlockTargetSyntax.splitLine("a:tnt, a:door[half=upper, open=true",
                warnings::add)).containsExactly("a:tnt");
        assertThat(warnings).hasSize(1);
    }

    @Test
    void ignoresEmptyFragments() {
        assertThat(BlockTargetSyntax.splitLine(" , a:tnt,, ", warnings::add)).containsExactly("a:tnt");
        assertThat(BlockTargetSyntax.splitLine("", warnings::add)).isEmpty();
    }

    @Test
    void parsesPlainBlock() {
        assertThat(BlockTargetSyntax.parse(" minecraft:torch ", warnings::add)).isEqualTo(new Target("minecraft:torch",
                false, List.of()));
    }

    @Test
    void parsesNamespaceWildcard() {
        assertThat(BlockTargetSyntax.parse("somemod:*", warnings::add)).isEqualTo(new Target("somemod:", true,
                List.of()));
    }

    @Test
    void parsesStateConditions() {
        Target target = BlockTargetSyntax.parse("minecraft:wooden_door[half=upper, open=true]", warnings::add);
        assertThat(target.id()).isEqualTo("minecraft:wooden_door");
        assertThat(target.wholeNamespace()).isFalse();
        assertThat(target.conditions()).containsExactly(new Condition("half", "upper"), new Condition("open", "true"));
        assertThat(warnings).isEmpty();
    }

    @Test
    void wildcardValuesAreNotConditions() {
        Target target = BlockTargetSyntax.parse("minecraft:lever[facing=*,powered=true]", warnings::add);
        assertThat(target.conditions()).containsExactly(new Condition("powered", "true"));
    }

    @Test
    void emptyBracketsMatchEveryState() {
        assertThat(BlockTargetSyntax.parse("minecraft:lever[]", warnings::add).conditions()).isEmpty();
        assertThat(warnings).isEmpty();
    }

    @Test
    void malformedConditionsAreReportedAndDropped() {
        Target target = BlockTargetSyntax.parse("minecraft:lever[powered,facing=north]", warnings::add);
        assertThat(target.conditions()).containsExactly(new Condition("facing", "north"));
        assertThat(warnings).hasSize(1);
    }

    @Test
    void missingEndBracketFallsBackToTheWholeBlock() {
        Target target = BlockTargetSyntax.parse("minecraft:lever[powered=true", warnings::add);
        assertThat(target).isEqualTo(new Target("minecraft:lever", false, List.of()));
        assertThat(warnings).hasSize(1);
    }

}
